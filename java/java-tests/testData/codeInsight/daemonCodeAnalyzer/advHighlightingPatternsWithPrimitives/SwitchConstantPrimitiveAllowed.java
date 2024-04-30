package dfa;

public class SwitchConstantPrimitiveAllowed {
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


    private static void testDoubleObject() {
        Double o = 0.0;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Double'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Double'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Double'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Double'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Double'">1.0f</error> -> System.out.println("1.0f"); //error
            case 1.0 -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testFloatObject() {
        Float o = 1F;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Float'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Float'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Float'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Float'">1l</error> -> System.out.println("1l"); //error
            case 1.0f -> System.out.println("1.0f");
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Float'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testLongObject() {
        Long o = 1L;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Long'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Long'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Long'">0xa</error> -> System.out.println("0xa"); //error
            case 1l -> System.out.println("1l");
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Long'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Long'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testShortObject() {
        Short o = 1;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Short'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Short'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Short'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Short'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testByteObject() {
        Byte o = '1';
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Byte'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Byte'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Byte'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Byte'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testCharObject() {
        Character o = '1';
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Character'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Character'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Character'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Character'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testIntObject() {
        Integer o = 1;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'java.lang.Integer'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Integer'">'a'</error> -> System.out.println("a"); //error
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Integer'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Integer'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Integer'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testBooleanObject() {
        Boolean o = true;
        switch (o) {
            case true -> System.out.println("true");
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Boolean'">1</error> -> System.out.println("1"); //error
            case <error descr="Incompatible types. Found: 'char', required: 'java.lang.Boolean'">'a'</error> -> System.out.println("a"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'java.lang.Boolean'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'java.lang.Boolean'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'java.lang.Boolean'">1.0f</error> -> System.out.println("1.0f"); //error
            case <error descr="Incompatible types. Found: 'double', required: 'java.lang.Boolean'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testDouble() {
        double o = 0.0;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'double'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'double'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'double'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'double'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'double'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'double'">1.0f</error> -> System.out.println("1.0f"); //error
            case 1.0 -> System.out.println("1.0f");
            default -> System.out.println("default");
        }
    }

    private static void testFloat() {
        float o = 1F;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'float'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'float'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'float'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'float'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'float'">1l</error> -> System.out.println("1l"); //error
            case 1.0f -> System.out.println("1.0f");
            case <error descr="Incompatible types. Found: 'double', required: 'float'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testLong() {
        long o = 1L;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'long'">true</error> -> System.out.println("true"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'long'">1</error> -> System.out.println("1");//error
            case <error descr="Incompatible types. Found: 'char', required: 'long'">'a'</error> -> System.out.println("a");//error
            case <error descr="Incompatible types. Found: 'int', required: 'long'">0xa</error> -> System.out.println("0xa"); //error
            case 1l -> System.out.println("1l");
            case <error descr="Incompatible types. Found: 'float', required: 'long'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'long'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testShort() {
        short o = 1;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'short'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'short'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'short'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'short'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testByte() {
        byte o = '1';
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'byte'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'byte'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'byte'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'byte'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testChar() {
        char o = '1';
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'char'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'char'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'char'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'char'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testInt() {
        int o = 1;
        switch (o) {
            case <error descr="Incompatible types. Found: 'boolean', required: 'int'">true</error> -> System.out.println("true"); //error
            case 1 -> System.out.println("1");
            case 'a' -> System.out.println("a");
            case 0xa -> System.out.println("0xa");
            case <error descr="Incompatible types. Found: 'long', required: 'int'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'int'">1.0f</error> -> System.out.println("1.0f");  //error
            case <error descr="Incompatible types. Found: 'double', required: 'int'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }

    private static void testBoolean() {
        boolean o = true;
        switch (o) {
            case true -> System.out.println("true");
            case <error descr="Incompatible types. Found: 'int', required: 'boolean'">1</error> -> System.out.println("1"); //error
            case <error descr="Incompatible types. Found: 'char', required: 'boolean'">'a'</error> -> System.out.println("a"); //error
            case <error descr="Incompatible types. Found: 'int', required: 'boolean'">0xa</error> -> System.out.println("0xa"); //error
            case <error descr="Incompatible types. Found: 'long', required: 'boolean'">1l</error> -> System.out.println("1l"); //error
            case <error descr="Incompatible types. Found: 'float', required: 'boolean'">1.0f</error> -> System.out.println("1.0f"); //error
            case <error descr="Incompatible types. Found: 'double', required: 'boolean'">1.0</error> -> System.out.println("1.0f"); //error
            default -> System.out.println("default");
        }
    }
}
