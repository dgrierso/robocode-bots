package v026091;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Random;

import robocode.AdvancedRobot;
import robocode.RobotDeathEvent;
import robocode.ScannedRobotEvent;
import robocode.control.BattlefieldSpecification;

/*
 * @author David Grierson
 * 
 * Description: E2 Module 2 robocode assignment for Team 2.
 * 
 * Parts of this code have been based upon code from:
 * https://web.archive.org/web/20080228144224/http://www.codepoet.org/~markw/weber/java/robocode/
 *
 */
public class DavidGrierson extends AdvancedRobot {
	private HashMap<String, AdvancedEnemyBot> enemies = new HashMap<String, AdvancedEnemyBot>();
	private String currentEnemy = "";
	private byte direction = 1;
	private Random generator = new Random((long) 850044);
	private int interval = generator.nextInt(20);
	private double battleFieldWidth;
	private double battleFieldHeight;
	private double myBearing;

	public void run() {
		/*
		 * Find out how big the playing field is.
		 */
		battleFieldWidth = getBattleFieldWidth();
		battleFieldHeight = getBattleFieldHeight();

		/*
		 * Set the JPMC colour scheme ;)
		 */
		setBodyColor(new Color(84, 48, 26));
		setGunColor(new Color(136, 171, 213));
		setRadarColor(new Color(109, 110, 113));
		setBulletColor(new Color(255, 255, 100));
		setScanColor(new Color(255, 200, 200));

		/*
		 * Enable turning the gun and the radar independently of the tank body.
		 */
		setAdjustGunForRobotTurn(true);
		setAdjustRadarForRobotTurn(true);

		/*
		 * We're not targeting anyone yet.
		 */
		currentEnemy = "";

		/*
		 * Main event loop.
		 */
		while (true) {
			setTurnRadarRight(90);
			moveRobot();
			adjustTargeting();
			execute();
		}
	}

	private void moveRobot() {
		/*
		 * If we've got no enemies to attack then don't move.
		 */
		if (enemies.isEmpty()) {
			setAhead(0);
			return;
		}

		/*
		 * Switch directions if we've stopped - hit a robot or hit a wall. OR
		 * strafe by changing direction every n ticks (where n is a random value
		 * between 1 and 20). Use a random time value so that our turn is less
		 * predictable.
		 */
		if (getVelocity() == 0 || getTime() % (interval + 1) == 0) {
			direction *= -1;
			interval = generator.nextInt(20);
			/*
			 * Set the distance to be moved to be a random value of between 100
			 * and 800.
			 * 
			 * This should be a bit more intelligent and figure out where in the
			 * arena would be the safest spot (farthest from most other robots
			 * perhaps). Could also try to adjust the movement and turning
			 * strategy based upon the number of enemies still alive.
			 * 
			 * Would like to improve this to detect whether we're close to a
			 * wall by looking at our current coordinates and the size of the
			 * game area.
			 */
			int nextint = Math.abs(generator.nextInt());
			int modulo = nextint % 400;

			/*
			 * Only move a maximum of 150 units per turn.
			 */
			double distance = Math.max(modulo, 400);

			setAhead(distance * direction);
		}
	}

	/**
	 * If the robot we're attacking dies we'll get an onRobotDeath event, in
	 * which case we want to reset the current target we're aiming at.
	 * 
	 * @see robocode.Robot#onRobotDeath(robocode.RobotDeathEvent)
	 */
	public void onRobotDeath(RobotDeathEvent e) {
		if ( currentEnemy.equals(e.getName()) ) {
			enemies.remove(currentEnemy);
			currentEnemy = "";
		}
	}

	/**
	 * Event handler which fires when a Robot is detected by the radar.
	 * 
	 * @see robocode.Robot#onScannedRobot(robocode.ScannedRobotEvent)
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
		/*
		 * If the enemy we've detected isn't on our list of enemies then add it
		 * to our list of enemies.
		 */
		if (enemies.containsKey(e.getName()) == false) {
			enemies.put(e.getName(), new AdvancedEnemyBot());
		}

		/*
		 * We can now infer that the enemy will be on the hashmap so we can
		 * update its details from the event we've just received.
		 */
		enemies.get(e.getName()).update(e, this);

		/*
		 * If we don't have a current enemy target ... OR the new target is
		 * closer than our current ... then assign this enemy as the name of the
		 * current enemy.
		 */
		if ( "".equals(currentEnemy) || (e.getDistance() < enemies.get(currentEnemy).getDistance()) ) {
			currentEnemy = e.getName();
		}
	}

	/**
	 * This method gets called each time round the main run loop ... the robot
	 * moves some distance each time through the loop - therefore the gun moves
	 * off target with each movement - consequently we want to adjust the gun
	 * with each movement of the robot as well.
	 */
	private void adjustTargeting() {
		/*
		 * If we haven't spotted an enemy yet - then do nothing.
		 */
		if ( "".equals(currentEnemy) ) {
			return;
		}

		/*
		 * Figure out how strong we should fire the bullet based upon how far
		 * away the target is (the more powerful the bullet the slower it goes)
		 * - then using the calculated firePower we can calculate what the
		 * bullet speed will be. We can use this to calculate the flight time.
		 */
		double distance = enemies.get(currentEnemy).getDistance();
		double firePower = Math.min(500 / (distance == 0 ? 0.00000001 : distance), 3);
		double bulletSpeed = 20 - firePower * 3;
		long time = (long) (enemies.get(currentEnemy).getDistance() / bulletSpeed);

		/*
		 * So now we've got the flight time we should predict where the target's
		 * going to be after that time based upon their current velocity and
		 * bearing.
		 */
		double futureX = enemies.get(currentEnemy).getFutureX(time);
		double futureY = enemies.get(currentEnemy).getFutureY(time);
		double absDeg = absoluteBearing(getX(), getY(), futureX, futureY);

		/*
		 * So now we should aim the gun at where we think the target is going to
		 * be.
		 */
		setTurnGunRight(normalizeBearing(absDeg - getGunHeading()));

		/*
		 * Turn our body perpendicular to the enemy we're aiming at in order to
		 * strafe them, but don't make it completely perpendicular so that we'll
		 * zig-zag into them during the doMove() calls.
		 */
		myBearing = normalizeBearing(enemies.get(currentEnemy).getBearing() + 90 - (15 * direction));
		setTurnRight(myBearing);

		/*
		 * Only fire the gun if it's cold and pointing in the right direction,
		 * otherwise we waste energy firing the gun when we're not pointing at
		 * the target.
		 * 
		 * Also don't fire the gun if we're down to a low level of energy so
		 * that we can keep moving in case we can win purely by attrition.
		 */
		if (getEnergy() > 1 && getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10) {
			/*
			 * Fire the gun with a power relative to the distance the target is
			 * away from us.
			 */
			setFire(firePower);
		}
	}

	private double absoluteBearing(double x1, double y1, double x2, double y2) {
		double xo = x2 - x1;
		double yo = y2 - y1;
		double hyp = Point2D.distance(x1, y1, x2, y2);
		double arcSin = Math.toDegrees(Math.asin(xo / hyp));
		double bearing = 0;

		if (xo > 0 && yo > 0) { // both pos: lower-Left
			bearing = arcSin;
		} else if (xo < 0 && yo > 0) { // x neg, y pos: lower-right
			bearing = 360 + arcSin; // arcsin is negative here, actuall 360 -
									// ang
		} else if (xo > 0 && yo < 0) { // x pos, y neg: upper-left
			bearing = 180 - arcSin;
		} else if (xo < 0 && yo < 0) { // both neg: upper-right
			bearing = 180 - arcSin; // arcsin is negative here, actually 180 +
									// ang
		}

		return bearing;
	}

	/**
	 * Code for normalising a bearing back to between -180 .. 180 degrees.
	 */
	private double normalizeBearing(double angle) {
		while (angle > 180) {
			angle -= 360;
		}
		while (angle < -180) {
			angle += 360;
		}
		return angle;
	}

	/**
	 * Victory dance.
	 */
	public void onWin() {
		setAhead(0);
		setTurnRight(36000);
		execute();
	}
}
