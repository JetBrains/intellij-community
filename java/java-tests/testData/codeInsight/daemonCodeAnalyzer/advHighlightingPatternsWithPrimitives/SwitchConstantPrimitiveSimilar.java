package dfa;

public class SwitchConstantPrimitiveSimilar {

    public static final long LONG = 2L + 3L;
    private static final Long LONG_OBJECT = LONG;

    public static void main(String[] args) {
        testBoolean();
        testInt();
        testChar();
        testByte();
        testShort();
        testLong();
        testFloat();
        testDouble();

        testBooleanObject();
        testIntObject();
        testCharObject();
        testByteObject();
        testShortObject();
        testLongObject();
        testFloatObject();
        testDoubleObject();
    }


    private static void testDouble() {
        double o = 0.0;
        switch (o) {
            case <error descr="Duplicate label '1'">1.0</error> -> System.out.println("1.0f");
            case <error descr="Duplicate label '1'">1.0</error> -> System.out.println("1.0f"); // error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1.0 -> System.out.println("1.0f");
            case 2.0 -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testFloat() {
        float o = 1F;
        switch (o) {
            case <error descr="Duplicate label '1'">1.0f</error> -> System.out.println("1.0f");
            case <error descr="Duplicate label '1'">1.0f</error> -> System.out.println("1.0f");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1.0f -> System.out.println("1.0f");
            case 2.0f -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testLong() {
        long o = 1L;
        switch (o) {
            case <error descr="Duplicate label '1'">1L</error> -> System.out.println("1l");
            case <error descr="Duplicate label '1'">1L</error> -> System.out.println("1l");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case 2L -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case 2L + 3L -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case LONG -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case <error descr="Constant expression required">LONG_OBJECT</error> -> System.out.println("1l");// error
            default -> System.out.println("default");
        }
    }

    private static void testShort() {
        short o = 1;
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 2 -> System.out.println("1");
            case 1 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testByte() {
        byte o = '1';
        switch (o) {
            case 1 -> System.out.println("1");
            case <error descr="Duplicate label 'a'">'a'</error> -> System.out.println("a");
            case <error descr="Duplicate label 'a'">'a'</error> -> System.out.println("a");// error
            case 0xa -> System.out.println("0xa");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 'b' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            default -> System.out.println("default");
        }
    }

    private static void testChar() {
        char o = '1';
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 2 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testInt() {
        int o = 1;
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 2 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testBoolean() {
        boolean o = true;
        switch (o) {
            case <error descr="Duplicate label 'true'">true</error> -> System.out.println("true");
            case <error descr="Duplicate label 'true'">true</error> -> System.out.println("true");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case false -> System.out.println("true");
            case true -> System.out.println("true");
        }
        switch (o) {
            case <error descr="Constant expression required">Boolean.TRUE</error> -> System.out.println("true");// error
            case true -> System.out.println("true");
        }
    }


    private static void testDoubleObject() {
        Double o = 0.0;
        switch (o) {
            case <error descr="Duplicate label '1'">1.0</error> -> System.out.println("1.0f");
            case <error descr="Duplicate label '1'">1.0</error> -> System.out.println("1.0f");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1.0 -> System.out.println("1.0f");
            case 2.0 -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testFloatObject() {
        Float o = 1F;
        switch (o) {
            case <error descr="Duplicate label '1'">1.0f</error> -> System.out.println("1.0f");
            case <error descr="Duplicate label '1'">1.0f</error> -> System.out.println("1.0f");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1.0f -> System.out.println("1.0f");
            case 2.0f -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testLongObject() {
        Long o = 1L;
        switch (o) {
            case <error descr="Duplicate label '1'">1L</error> -> System.out.println("1l");
            case <error descr="Duplicate label '1'">1L</error> -> System.out.println("1l");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case 2L -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case 2L + 3L -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case LONG -> System.out.println("1l");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1L -> System.out.println("1l");
            case <error descr="Constant expression required">LONG_OBJECT</error> -> System.out.println("1l");// error
            default -> System.out.println("default");
        }
    }

    private static void testShortObject() {
        Short o = 1;
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 2 -> System.out.println("1");
            case 1 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testByteObject() {
        Byte o = '1';
        switch (o) {
            case 1 -> System.out.println("1");
            case <error descr="Duplicate label 'a'">'a'</error> -> System.out.println("a");
            case <error descr="Duplicate label 'a'">'a'</error> -> System.out.println("a");// error
            case 0xa -> System.out.println("0xa");
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 'b' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            default -> System.out.println("default");
        }
    }

    private static void testCharObject() {
        Character o = '1';
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 2 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testIntObject() {
        Integer o = 1;
        switch (o) {
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");
            case <error descr="Duplicate label '1'">1</error> -> System.out.println("1");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case 1 -> System.out.println("1");
            case 2 -> System.out.println("1");
            default -> System.out.println("default");
        }
    }

    private static void testBooleanObject() {
        Boolean o = true;
        switch (o) {
            case <error descr="Duplicate label 'true'">true</error> -> System.out.println("true");
            case <error descr="Duplicate label 'true'">true</error> -> System.out.println("true");// error
            default -> System.out.println("default");
        }
        switch (o) {
            case false -> System.out.println("true");
            case true -> System.out.println("true");
        }
        switch (o) {
            case <error descr="Constant expression required">Boolean.TRUE</error> -> System.out.println("true");// error
            case true -> System.out.println("true");
        }
    }
}
