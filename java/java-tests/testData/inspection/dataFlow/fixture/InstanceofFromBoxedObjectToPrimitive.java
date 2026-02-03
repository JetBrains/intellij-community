package dfa;

public class InstanceofFromBoxedObjectToPrimitive {
    public static void main(String[] args) {
        System.out.println("testIntegerMin");
        testIntegerMin();
        System.out.println("testIntegerMax");
        testIntegerMax();
        System.out.println("testIntegerNull");
        testIntegerNull();
        System.out.println("testCharMin");
        testCharMin();
        System.out.println("testCharMax");
        testCharMax();
        System.out.println("testByteMin");
        testByteMin();
        System.out.println("testByteMax");
        testByteMax();
        System.out.println("testShortMin");
        testShortMin();
        System.out.println("testShortMax");
        testShortMax();
        System.out.println("testLongMin");
        testLongMin();
        System.out.println("testLongMax");
        testLongMax();
        System.out.println("testFloatMin");
        testFloatMin();
        System.out.println("testFloatMax");
        testFloatMax();
        System.out.println("testDoubleMin");
        testDoubleMin();
        System.out.println("testDoubleMax");
        testDoubleMax();
        System.out.println("testBooleanMin");
        testBooleanMin();
        System.out.println("testBooleanMax");
        testBooleanMax();
    }

    private static void testBooleanMin() {
        Boolean o = false;
        if (<warning descr="Condition 'o instanceof boolean b' is always 'true'">o instanceof boolean b</warning>) { //true
            System.out.println("boolean");
        }
    }

    private static void testBooleanMax() {
        Boolean o = true;
        if (<warning descr="Condition 'o instanceof boolean b' is always 'true'">o instanceof boolean b</warning>) { //true
            System.out.println("boolean");
        }
    }

    private static void testDoubleMin() {
        Double o = 0.0;
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testDoubleMax() {
        Double o = Double.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testFloatMin() {
        Float o = 0.0F;
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testFloatMax() {
        Float o = Float.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testLongMin() {
        Long o = 0l;
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testLongMax() {
        Long o = Long.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) { //false
            System.out.println("double");
        }
    }

    private static void testShortMin() {
        Short o = 0;
        if (<warning descr="Condition 'o instanceof int i' is always 'true'">o instanceof int i</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'true'">o instanceof short</warning>) { //true
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testShortMax() {
        Short o = Short.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int i' is always 'true'">o instanceof int i</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'true'">o instanceof short</warning>) { //true
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testByteMin() {
        Byte o = 0;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'true'">o instanceof byte</warning>) { //true
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'true'">o instanceof short</warning>) { //true
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testByteMax() {
        Byte o = Byte.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'true'">o instanceof byte</warning>) { //true
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'true'">o instanceof short</warning>) { //true
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) { //true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) { //true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
    }

    private static void testCharMin() {
        Character o = 'c';
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testCharMax() {
        Character o = Character.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'true'">o instanceof char t</warning>) { //true
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'true'">o instanceof long t</warning>) { //true
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'true'">o instanceof float t</warning>) { //true
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
    }

    private static void testIntegerMin() {
        Integer o = 0;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) {//true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'true'">o instanceof float</warning>) {//true
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) {//true
            System.out.println("double");
        }
    }

    private static void testIntegerMax() {
        Integer o = Integer.MAX_VALUE;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'true'">o instanceof long</warning>) {//true
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) {//false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) {//true
            System.out.println("double");
        }
    }

    private static void testIntegerNull() {
        Integer o = null;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) {//false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) {//false
            System.out.println("float");
        }
        System.out.println("1");
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) {//false
            System.out.println("double");
        }
    }

    private static void testLongNull() {
        Long o = null;
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) {//false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) {//false
            System.out.println("float");
        }
        System.out.println("1");
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) {//false
            System.out.println("double");
        }
        System.out.println("2");
    }
}
