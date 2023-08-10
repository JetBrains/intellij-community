public class Test {

    public static final int ONE = 1;
    public static final int TWO = 2;
    public static final int THREE = 3;

    public static String numString(int num) {
        return switch (num) {
            case ONE -> "one";
            case TWO -> "two";
            case THREE -> "three";
            default -> throw new AssertionError("unknown constant");
        };
    }

    public static void main(String[] args) {
        System.out.println(numString(ONE));
        System.out.println(numString(TWO));
        System.out.println(numString(THREE));
    }

}