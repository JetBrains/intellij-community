public class X extends Y {}
public class Y {

	private static int x = 0;

	public static int getX() {
		return x;
	}

	public static void setX(int x) {
		X.<error descr="'x' has private access in 'Y'">x</error> = x;
	}
}