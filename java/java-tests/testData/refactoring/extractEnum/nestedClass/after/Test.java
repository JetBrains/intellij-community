public class Test {

    public static String numString(EEnum num) {
        switch (num) {
        case ONE:   return "one";
        case TWO:   return "two";
        case THREE: return "three";
        default:    throw new AssertionError("unknown constant");
        }
    }

    public static void main(String[] args) {
        System.out.println(numString(EEnum.ONE));
        System.out.println(numString(EEnum.TWO));
        System.out.println(numString(EEnum.THREE));
    }

    public enum EEnum {
        ONE(1), TWO(2), THREE(3);
        private int value;

        public int getValue() {
            return value;
        }

        EEnum(int value) {
            this.value = value;
        }
    }
}