package dfa;

public class InstanceofPrimitivesNotAllowed {
    public static void main(String[] args) {
        testPrimitiveToPrimitive();
        testPrimitiveToObject();
        testObjectToPrimitive();
    }

    private static void testObjectToPrimitive() {
        Integer i = 1;
        Object l = 1;
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'double'">i instanceof double</error>) { //error
            System.out.println("double");
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'int'">i instanceof int</error>) { //error
            System.out.println("int");
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Integer' to 'int'">i instanceof int ii</error>) { //error
            System.out.println("int pattern");
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'double'">l instanceof double</error>) { //error
            System.out.println("double");
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">l instanceof int</error>) { //error
            System.out.println("int");
        }
        if (<error descr="Inconvertible types; cannot cast 'java.lang.Object' to 'int'">l instanceof int ii</error>) { //error
            System.out.println("int pattern");
        }
    }

    private static void testPrimitiveToPrimitive() {
        int i = 1;
        long l = 1;
        if (<error descr="Inconvertible types; cannot cast 'int' to 'double'">i instanceof double</error>) { //error
            System.out.println("double");
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'int'">i instanceof int</error>) { //error
            System.out.println("int");
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'int'">i instanceof int ii</error>) { //error
            System.out.println("int pattern");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'double'">l instanceof double</error>) { //error
            System.out.println("double");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'int'">l instanceof int</error>) { //error
            System.out.println("int");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'int'">l instanceof int ii</error>) { //error
            System.out.println("int pattern");
        }
    }

    private static void testPrimitiveToObject() {
        int i = 1;
        long l = 1;
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Double'">i instanceof Double</error>) { //error
            System.out.println("Double");
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Integer'">i instanceof Integer</error>) { //error
            System.out.println("Integer");
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Object'">i instanceof Object</error>) { //error
            System.out.println("Object");
        }
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Integer'">i instanceof Integer ii</error>) { //error
            System.out.println("Integer ii");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Double'">l instanceof Double</error>) { //error
            System.out.println("Double");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">l instanceof Integer</error>) { //error
            System.out.println("Integer");
        }
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Object'">l instanceof Object</error>) { //error
            System.out.println("Object");
        }
    }
}
