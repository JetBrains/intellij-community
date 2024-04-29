public class SimplePrimitiveInstanceOf {
    public static void main(String[] args) {
        testForBool(true);
        testForInt(1);
        testForLong(1);
        testForDouble(1);
        testForFloat(1);
        testForByte((byte) 1);
        testForChar((char) 1);
        testForShort((short) 1);

        testForBoolObject(true);
        testForIntObject(1);
        testForLongObject(1L);
        testForDoubleObject(1d);
        testForFloatObject(1f);
        testForByteObject((byte) 1);
        testForCharObject((char) 1);
        testForShortObject((short) 1);

        testForObject(1);
        testForNumber(1);
    }

    private static void testForBool(boolean source) {
        if (source instanceof boolean) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'int'">source instanceof int</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'long'">source instanceof long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'double'">source instanceof double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'float'">source instanceof float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (source instanceof Boolean) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Number'">source instanceof Number</error>) { //error
            System.out.println();
        }
    }

    private static void testForInt(int source) {
        if (<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof Integer) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForLong(long source) {
        if (<error descr="Inconvertible types; cannot cast 'long' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (source instanceof Long) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForDouble(double source) {
        if (<error descr="Inconvertible types; cannot cast 'double' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (source instanceof Double) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForFloat(float source) {
        if (<error descr="Inconvertible types; cannot cast 'float' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (source instanceof Float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForByte(byte source) {
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (source instanceof Byte) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForChar(char source) {
        if (<error descr="Inconvertible types; cannot cast 'char' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (source instanceof Character) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Number'">source instanceof Number</error>) { //error
            System.out.println();
        }
    }

    private static void testForShort(short source) {
        if (<error descr="Inconvertible types; cannot cast 'short' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (source instanceof Short) {
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForBoolObject(Boolean source) {
        if (source instanceof boolean) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'int'">source instanceof int</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'long'">source instanceof long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'double'">source instanceof double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'float'">source instanceof float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (source instanceof Boolean) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Number'">source instanceof Number</error>) { //error
            System.out.println();
        }
    }

    private static void testForIntObject(Integer source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof Integer) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForLongObject(Long source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">source instanceof int</error>) { //error
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (source instanceof Long) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForDoubleObject(Double source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">source instanceof int</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'long'">source instanceof long</error>) { //error
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'float'">source instanceof float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (source instanceof Double) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForFloatObject(Float source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'int'">source instanceof int</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'long'">source instanceof long</error>) { //error
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (source instanceof Float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForByteObject(Byte source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (source instanceof Byte) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForCharObject(Character source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'short'">source instanceof short</error>) { //error
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (source instanceof Character) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Short'">source instanceof Short</error>) { //error
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Number'">source instanceof Number</error>) { //error
            System.out.println();
        }
    }

    private static void testForShortObject(Short source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'byte'">source instanceof byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Integer'">source instanceof Integer</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Long'">source instanceof Long</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Double'">source instanceof Double</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Float'">source instanceof Float</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Byte'">source instanceof Byte</error>) { //error
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (source instanceof Short) {
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForObject(Object source) {
        if (source instanceof boolean) {
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (source instanceof char) {
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (source instanceof Boolean) {
            System.out.println();
        }
        if (source instanceof Integer) {
            System.out.println();
        }
        if (source instanceof Long) {
            System.out.println();
        }
        if (source instanceof Double) {
            System.out.println();
        }
        if (source instanceof Float) {
            System.out.println();
        }
        if (source instanceof Byte) {
            System.out.println();
        }
        if (source instanceof Character) {
            System.out.println();
        }
        if (source instanceof Short) {
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }

    private static void testForNumber(Number source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'boolean'">source instanceof boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof int) {
            System.out.println();
        }
        if (source instanceof long) {
            System.out.println();
        }
        if (source instanceof double) {
            System.out.println();
        }
        if (source instanceof float) {
            System.out.println();
        }
        if (source instanceof byte) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'char'">source instanceof char</error>) { //error
            System.out.println();
        }
        if (source instanceof short) {
            System.out.println();
        }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'java.lang.Boolean'">source instanceof Boolean</error>) { //error
            System.out.println();
        }
        if (source instanceof Integer) {
            System.out.println();
        }
        if (source instanceof Long) {
            System.out.println();
        }
        if (source instanceof Double) {
            System.out.println();
        }
        if (source instanceof Float) {
            System.out.println();
        }
        if (source instanceof Byte) {
            System.out.println();
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'java.lang.Character'">source instanceof Character</error>) { //error
            System.out.println();
        }
        if (source instanceof Short) {
            System.out.println();
        }
        if (source instanceof Object) {
            System.out.println();
        }
        if (source instanceof Number) {
            System.out.println();
        }
    }
}
