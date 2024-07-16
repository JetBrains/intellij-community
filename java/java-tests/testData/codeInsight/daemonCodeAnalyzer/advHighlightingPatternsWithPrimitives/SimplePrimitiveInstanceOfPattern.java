public class SimplePrimitiveInstanceOfPattern {
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
        if (source instanceof boolean target) { }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'int'">source instanceof int target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'long'">source instanceof long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'double'">source instanceof double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'float'">source instanceof float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'short'">source instanceof short target</error>) { } //error

        if (source instanceof Boolean target) { }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (<error descr="Inconvertible types; cannot cast 'boolean' to 'java.lang.Number'">source instanceof Number target</error>) { } //error
    }

    private static void testForInt(int source) {
        if (<error descr="Inconvertible types; cannot cast 'int' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (source instanceof Integer target) { }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForLong(long source) {
        if (<error descr="Inconvertible types; cannot cast 'long' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (source instanceof Long target) { }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForDouble(double source) {
        if (<error descr="Inconvertible types; cannot cast 'double' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (source instanceof Double target) { }
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'double' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForFloat(float source) {
        if (<error descr="Inconvertible types; cannot cast 'float' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (source instanceof Float target) { }
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'float' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForByte(byte source) {
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (source instanceof Byte target) { }
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'byte' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForChar(char source) {
        if (<error descr="Inconvertible types; cannot cast 'char' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (source instanceof Character target) { }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (<error descr="Inconvertible types; cannot cast 'char' to 'java.lang.Number'">source instanceof Number target</error>) { } //error
    }

    private static void testForShort(short source) {
        if (<error descr="Inconvertible types; cannot cast 'short' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'short' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (source instanceof Short target) { }
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForBoolObject(Boolean source) {
        if (source instanceof boolean target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'int'">source instanceof int target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'long'">source instanceof long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'double'">source instanceof double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'float'">source instanceof float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'short'">source instanceof short target</error>) { } //error

        if (source instanceof Boolean target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Boolean' to 'java.lang.Number'">source instanceof Number target</error>) { } //error
    }

    private static void testForIntObject(Integer source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'short'">source instanceof short target</error>) { } //error

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (source instanceof Integer target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForLongObject(Long source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'int'">source instanceof int target</error>) { } //error
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'short'">source instanceof short target</error>) { } //error

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (source instanceof Long target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Long' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForDoubleObject(Double source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'int'">source instanceof int target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'long'">source instanceof long target</error>) { } //error
        if (source instanceof double target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'float'">source instanceof float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'short'">source instanceof short target</error>) { } //error

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (source instanceof Double target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Double' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForFloatObject(Float source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'int'">source instanceof int target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'long'">source instanceof long target</error>) { } //error
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'char'">source instanceof char target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'short'">source instanceof short target</error>) { } //error

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (source instanceof Float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Float' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForByteObject(Byte source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'char'">source instanceof char target</error>) { } //error
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (source instanceof Byte target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Byte' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForCharObject(Character source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'byte'">source instanceof byte target</error>) { } //error
        if (source instanceof char target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'short'">source instanceof short target</error>) { } //error

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (source instanceof Character target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Short'">source instanceof Short target</error>) { } //error
        if (source instanceof Object target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Character' to 'java.lang.Number'">source instanceof Number target</error>) { } //error
    }

    private static void testForShortObject(Short source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'byte'">source instanceof byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'char'">source instanceof char target</error>) { } //error
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Integer'">source instanceof Integer target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Long'">source instanceof Long target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Double'">source instanceof Double target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Float'">source instanceof Float target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Byte'">source instanceof Byte target</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Short' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (source instanceof Short target) { }
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForObject(Object source) {
        if (source instanceof boolean target) { }
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (source instanceof char target) { }
        if (source instanceof short target) { }

        if (source instanceof Boolean target) { }
        if (source instanceof Integer target) { }
        if (source instanceof Long target) { }
        if (source instanceof Double target) { }
        if (source instanceof Float target) { }
        if (source instanceof Byte target) { }
        if (source instanceof Character target) { }
        if (source instanceof Short target) { }
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }

    private static void testForNumber(Number source) {
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'boolean'">source instanceof boolean target</error>) { } //error
        if (source instanceof int target) { }
        if (source instanceof long target) { }
        if (source instanceof double target) { }
        if (source instanceof float target) { }
        if (source instanceof byte target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'char'">source instanceof char target</error>) { } //error
        if (source instanceof short target) { }

        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'java.lang.Boolean'">source instanceof Boolean target</error>) { } //error
        if (source instanceof Integer target) { }
        if (source instanceof Long target) { }
        if (source instanceof Double target) { }
        if (source instanceof Float target) { }
        if (source instanceof Byte target) { }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Number' to 'java.lang.Character'">source instanceof Character target</error>) { } //error
        if (source instanceof Short target) { }
        if (source instanceof Object target) { }
        if (source instanceof Number target) { }
    }
}
