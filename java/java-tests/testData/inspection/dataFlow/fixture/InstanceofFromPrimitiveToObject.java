package dfa;

public class InstanceofFromPrimitiveToObject {
    public static void main(String[] args) {
        testSimple();
        testInt();
        testChar();
        testByte();
        testShort();
        testLong();
        testFloat();
        testDouble();

        testRecordInt();
        testRecordLong();
        testRecordFloat();
    }

    private static void testRecordInt() {
        RecordInt a = new RecordInt(1);
        if (<warning descr="Condition 'a instanceof RecordInt(Object o)' is always 'true'">a instanceof RecordInt(Object o)</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof RecordInt(Number o)' is always 'true'">a instanceof RecordInt(Number o)</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof RecordInt(Integer o)' is always 'true'">a instanceof RecordInt(Integer o)</warning>) { //true
            System.out.println("Integer");
        }
    }

    private static void testRecordLong() {
        RecordLong a = new RecordLong(1);
        if (<warning descr="Condition 'a instanceof RecordLong(Object o)' is always 'true'">a instanceof RecordLong(Object o)</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof RecordLong(Number o)' is always 'true'">a instanceof RecordLong(Number o)</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof RecordLong(Long o)' is always 'true'">a instanceof RecordLong(Long o)</warning>) { //true
            System.out.println("Long");
        }
    }

    private static void testRecordFloat() {
        RecordFloat a = new RecordFloat(1);
        if (<warning descr="Condition 'a instanceof RecordFloat(Object o)' is always 'true'">a instanceof RecordFloat(Object o)</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof RecordFloat(Number o)' is always 'true'">a instanceof RecordFloat(Number o)</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof RecordFloat(Float o)' is always 'true'">a instanceof RecordFloat(Float o)</warning>) { //true
            System.out.println("Long");
        }
    }

    record RecordInt(int a){}
    record RecordLong(long a){}
    record RecordFloat(float a){}



    private static void testDouble() {
        double a = Double.MAX_VALUE;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Double' is always 'true'">a instanceof Double</warning>) { //true
            System.out.println("Double");
        }
    }

    private static void testFloat() {
        float a = 1;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Float' is always 'true'">a instanceof Float</warning>) { //true
            System.out.println("Float");
        }
    }

    private static void testLong() {
        long a = 1;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Long' is always 'true'">a instanceof Long</warning>) { //true
            System.out.println("Long");
        }
    }

    private static void testShort() {
        short a = 1;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Short' is always 'true'">a instanceof Short</warning>) { //true
            System.out.println("Short");
        }
    }

    private static void testByte() {
        byte a = 1;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Byte' is always 'true'">a instanceof Byte</warning>) { //true
            System.out.println("Byte");
        }
    }


    private static void testChar() {
        char a = Character.MAX_VALUE;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Character' is always 'true'">a instanceof Character</warning>) { //true
            System.out.println("Character");
        }
    }

    private static void testInt() {
        int a = 1;
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Integer' is always 'true'">a instanceof Integer</warning>) { //true
            System.out.println("Integer");
        }
    }

    private static void testSimple() {
        int a = getInt();
        if (<warning descr="Condition 'a instanceof Object o' is always 'true'">a instanceof Object o</warning>) { //true
            System.out.println("Object");
        }
        if (<warning descr="Condition 'a instanceof Number' is always 'true'">a instanceof Number</warning>) { //true
            System.out.println("Number");
        }
        if (<warning descr="Condition 'a instanceof Integer' is always 'true'">a instanceof Integer</warning>) { //true
            System.out.println("Integer");
        }
    }

    private static int getInt() {
        return 0;
    }
}
