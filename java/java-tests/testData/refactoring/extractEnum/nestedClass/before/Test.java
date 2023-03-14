public class Test {

    public static final int ONE = 1;
    public static final int TWO = 2;
    public static final int THREE = 3;

    public static String numString(int num) {
        switch (num) {
        case ONE:   return "one";
        case TWO:   return "two";
        case THREE: return "three";
        default:    throw new AssertionError("unknown constant");
        }
    }

    public static void main(String[] args) {
        System.out.println(numString(ONE));
        System.out.println(numString(TWO));
        System.out.println(numString(THREE));
    }

}