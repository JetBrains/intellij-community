public class Bar {
    private static final int INITIALIZED_CONST = 0;
    private static int initializedStaticField = 0;
    static {
        System.out.println(Bar.INITIALIZED_CONST);
        System.out.println(Bar.initializedStaticField);
    }
} 