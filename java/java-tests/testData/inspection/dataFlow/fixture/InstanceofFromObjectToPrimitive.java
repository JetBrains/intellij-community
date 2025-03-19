package dfa;

public class InstanceofFromObjectToPrimitive {
    public static void main(String[] args) {
        testSimple(0);
        testObjectNull();
        testObjectBoolean();
        testNumberInt();
        testObjectInt();
        testObjectDouble();
        testObjectBooleanPattern();
        testNumberIntPattern();
        testObjectIntPattern();
        testObjectNullPattern();
        testObjectDoublePattern();

        testObjectRecord();
        testNumberRecord();
        testIntegerRecord();
        testIntegerRecordNotNull();
        testLongRecord();
        testLongRecordNotNull();
    }

    private static void testObjectRecord() {
        ObjectRecord o = new ObjectRecord(1);
        if (o instanceof ObjectRecord(int a)) {
            System.out.println("int");
        }
        if (o instanceof ObjectRecord(byte a)) {
            System.out.println("byte");
        }
        if (o instanceof ObjectRecord(short a)) {
            System.out.println("short");
        }
        if (o instanceof ObjectRecord(long a)) {
            System.out.println("long");
        }
        if (o instanceof ObjectRecord(float a)) {
            System.out.println("float");
        }
        if (o instanceof ObjectRecord(double a)) {
            System.out.println("double");
        }
    }

    private static void testNumberRecord() {
        NumberRecord o = new NumberRecord(1);
        if (o instanceof NumberRecord(int a)) {
            System.out.println("int");
        }
        if (o instanceof NumberRecord(byte a)) {
            System.out.println("byte");
        }
        if (o instanceof NumberRecord(short a)) {
            System.out.println("short");
        }
        if (o instanceof NumberRecord(long a)) {
            System.out.println("long");
        }
        if (o instanceof NumberRecord(float a)) {
            System.out.println("float");
        }
        if (o instanceof NumberRecord(double a)) {
            System.out.println("double");
        }
    }

    private static void testIntegerRecord() {
        IntegerRecord o = new IntegerRecord(1);
        if (o instanceof IntegerRecord(int a)) {
            System.out.println("int");
        }
        if (o instanceof IntegerRecord(long a)) {
            System.out.println("long");
        }
        if (o instanceof IntegerRecord(float a)) {
            System.out.println("float");
        }
        if (o instanceof IntegerRecord(double a)) {
            System.out.println("double");
        }
    }

    private static void testLongRecord() {
        LongRecord o = new LongRecord(1L);
        if (o instanceof LongRecord(long a)) {
            System.out.println("long");
        }
        if (o instanceof LongRecord(float a)) {
            System.out.println("float");
        }
        if (o instanceof LongRecord(double a)) {
            System.out.println("double");
        }
    }

    private static void testLongRecordNotNull() {
        LongRecord o = new LongRecord(1L);
        if (o.o() == null) {
            return;
        }
        if (<warning descr="Condition 'o instanceof LongRecord(long a)' is always 'true'">o instanceof LongRecord(long a)</warning>) { //true
            System.out.println("long");
        }
        if (o instanceof LongRecord(float a)) {
            System.out.println("float");
        }
        if (o instanceof LongRecord(double a)) {
            System.out.println("double");
        }
    }

    private static void testDirectFieldAccess() {
        LongRecord o = new LongRecord(1L);
        if (o.o == null) {
            return;
        }
        if (<warning descr="Condition 'o instanceof LongRecord(long a)' is always 'true'">o instanceof LongRecord(long a)</warning>) { //true
            System.out.println("long");
        }
    }

    private static void testIntegerRecordNotNull() {
        IntegerRecord o = new IntegerRecord(1);
        if (o.o() == null) {
            return;
        }
        if (<warning descr="Condition 'o instanceof IntegerRecord(int a)' is always 'true'">o instanceof IntegerRecord(int a)</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof IntegerRecord(long a)' is always 'true'">o instanceof IntegerRecord(long a)</warning>) { //true
            System.out.println("long");
        }
        if (o instanceof IntegerRecord(float a)) {
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof IntegerRecord(double a)' is always 'true'">o instanceof IntegerRecord(double a)</warning>) {//true
            System.out.println("double");
        }
    }

    record ObjectRecord(Object o) {
    }

    record NumberRecord(Number o) {
    }

    record IntegerRecord(Integer o) {
    }

    record LongRecord(Long o) {
    }

    private static void testSimple(Object i) {
        if (i instanceof int) {
            System.out.println(i);
        }
    }

    public static void testObjectNull() {
        Object o = null;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char' is always 'false'">o instanceof char</warning>) { //false
            System.out.println("char");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'false'">o instanceof byte</warning>) { //false
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'false'">o instanceof short</warning>) { //false
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) { //false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) { //false
            System.out.println("double");
        }
        if (<warning descr="Condition 'o instanceof boolean' is always 'false'">o instanceof boolean</warning>) { //false
            System.out.println("boolean");
        }
    }


    public static void testObjectInt() {
        Object o = 1;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char' is always 'false'">o instanceof char</warning>) { //false
            System.out.println("char");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'false'">o instanceof byte</warning>) { //false
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'false'">o instanceof short</warning>) { //false
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) { //false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) { //false
            System.out.println("double");
        }
        if (<warning descr="Condition 'o instanceof boolean' is always 'false'">o instanceof boolean</warning>) { //false
            System.out.println("boolean");
        }
    }

    public static void testNumberInt() {
        Number o = 1;
        if (<warning descr="Condition 'o instanceof int' is always 'true'">o instanceof int</warning>) { //true
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'false'">o instanceof byte</warning>) { //false
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'false'">o instanceof short</warning>) { //false
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) { //false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) { //false
            System.out.println("double");
        }
    }

    public static void testObjectBoolean() {
        Object o = true;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char' is always 'false'">o instanceof char</warning>) { //false
            System.out.println("char");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'false'">o instanceof byte</warning>) { //false
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'false'">o instanceof short</warning>) { //false
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) { //false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'false'">o instanceof double</warning>) { //false
            System.out.println("double");
        }
        if (<warning descr="Condition 'o instanceof boolean' is always 'true'">o instanceof boolean</warning>) { //true
            System.out.println("boolean");
        }
    }

    public static void testObjectDouble() {
        Object o = 1.0;
        if (<warning descr="Condition 'o instanceof int' is always 'false'">o instanceof int</warning>) { //false
            System.out.println("int");
        }
        if (<warning descr="Condition 'o instanceof char' is always 'false'">o instanceof char</warning>) { //false
            System.out.println("char");
        }
        if (<warning descr="Condition 'o instanceof byte' is always 'false'">o instanceof byte</warning>) { //false
            System.out.println("byte");
        }
        if (<warning descr="Condition 'o instanceof short' is always 'false'">o instanceof short</warning>) { //false
            System.out.println("short");
        }
        if (<warning descr="Condition 'o instanceof long' is always 'false'">o instanceof long</warning>) { //false
            System.out.println("long");
        }
        if (<warning descr="Condition 'o instanceof float' is always 'false'">o instanceof float</warning>) { //false
            System.out.println("float");
        }
        if (<warning descr="Condition 'o instanceof double' is always 'true'">o instanceof double</warning>) { //true
            System.out.println("double");
        }
        if (<warning descr="Condition 'o instanceof boolean' is always 'false'">o instanceof boolean</warning>) { //false
            System.out.println("boolean");
        }
    }


    public static void testObjectIntPattern() {
        Object o = 1;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'false'">o instanceof double t</warning>) { //false
            System.out.println("double" + t);
        }
        if (<warning descr="Condition 'o instanceof boolean t' is always 'false'">o instanceof boolean t</warning>) { //false
            System.out.println("boolean" + t);
        }
    }

    public static void testObjectNullPattern() {
        Object o = null;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'false'">o instanceof double t</warning>) { //false
            System.out.println("double" + t);
        }
        if (<warning descr="Condition 'o instanceof boolean t' is always 'false'">o instanceof boolean t</warning>) { //false
            System.out.println("boolean" + t);
        }
    }

    public static void testNumberIntPattern() {
        Number o = 1;
        if (<warning descr="Condition 'o instanceof int t' is always 'true'">o instanceof int t</warning>) { //true
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'false'">o instanceof double t</warning>) { //false
            System.out.println("double" + t);
        }
    }

    public static void testObjectBooleanPattern() {
        Object o = true;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'false'">o instanceof double t</warning>) { //false
            System.out.println("double" + t);
        }
        if (<warning descr="Condition 'o instanceof boolean t' is always 'true'">o instanceof boolean t</warning>) { //true
            System.out.println("boolean" + t);
        }
    }

    public static void testObjectDoublePattern() {
        Object o = 1.0;
        if (<warning descr="Condition 'o instanceof int t' is always 'false'">o instanceof int t</warning>) { //false
            System.out.println("int" + t);
        }
        if (<warning descr="Condition 'o instanceof char t' is always 'false'">o instanceof char t</warning>) { //false
            System.out.println("char" + t);
        }
        if (<warning descr="Condition 'o instanceof byte t' is always 'false'">o instanceof byte t</warning>) { //false
            System.out.println("byte" + t);
        }
        if (<warning descr="Condition 'o instanceof short t' is always 'false'">o instanceof short t</warning>) { //false
            System.out.println("short" + t);
        }
        if (<warning descr="Condition 'o instanceof long t' is always 'false'">o instanceof long t</warning>) { //false
            System.out.println("long" + t);
        }
        if (<warning descr="Condition 'o instanceof float t' is always 'false'">o instanceof float t</warning>) { //false
            System.out.println("float" + t);
        }
        if (<warning descr="Condition 'o instanceof double t' is always 'true'">o instanceof double t</warning>) { //true
            System.out.println("double" + t);
        }
        if (<warning descr="Condition 'o instanceof boolean t' is always 'false'">o instanceof boolean t</warning>) { //false
            System.out.println("boolean" + t);
        }
    }
}
