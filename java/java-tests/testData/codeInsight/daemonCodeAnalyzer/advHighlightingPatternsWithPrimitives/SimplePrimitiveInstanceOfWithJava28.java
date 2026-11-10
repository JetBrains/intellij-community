public class SimplePrimitiveInstanceOf {
    public static void main(String[] args) {
        testForBool(true);
    }

    private static void testForBool(boolean source) {
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '28'">source instanceof boolean</error>) { }
        if (<error descr="Primitive types in patterns, instanceof and switch are not supported at language level '28'">source instanceof Boolean</error>) { }
    }
}
