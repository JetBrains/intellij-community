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
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof double</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof int</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof int ii</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof double</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof int</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof int ii</error>) { } //error
    }

    private static void testPrimitiveToPrimitive() {
        int i = 1;
        long l = 1;
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof double</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof int</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof int ii</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof double</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof int</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof int ii</error>) { } //error
    }

    private static void testPrimitiveToObject() {
        int i = 1;
        long l = 1;
        if (<error descr="Inconvertible types; cannot cast 'int' to 'java.lang.Double'">i instanceof Double</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof Integer</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof Object</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">i instanceof Integer ii</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Double'">l instanceof Double</error>) { } //error
        if (<error descr="Inconvertible types; cannot cast 'long' to 'java.lang.Integer'">l instanceof Integer</error>) { } //error
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '22'">l instanceof Object</error>) { } //error
    }
}
