public class Foo {
    private static final int INITIALIZED_CONST = 0;
    private static int initializedStaticField = 0;
    static {
        System.out.println(Foo.INITIALIZED_CONST);
        System.out.println(Foo.initializedStaticField);
    }
} 