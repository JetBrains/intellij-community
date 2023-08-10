public class Test {

    public static String numString(EEnum num) {
        return switch (num) {
            case ONE -> "one";
            case TWO -> "two";
            case THREE -> "three";
            default -> throw new AssertionError("unknown constant");
        };
    }

    public static void main(String[] args) {
        System.out.println(numString(EEnum.ONE));
        System.out.println(numString(EEnum.TWO));
        System.out.println(numString(EEnum.THREE));
    }

    public enum EEnum {
        ONE(1), TWO(2), THREE(3);
        private final int value;

        EEnum(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}