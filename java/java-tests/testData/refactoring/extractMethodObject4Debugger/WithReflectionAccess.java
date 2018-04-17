public class WithReflectionAccess {
    private int field = 10;

    private WithReflectionAccess(int value) {
        field = value;
    }

    public static void main(String[] args) {
        WithReflectionAccess instance = new WithReflectionAccess(2000);
        <caret>int a = 42;
    }

    private static void method() {

    }

    private int method(int arg) {
        return 0;
    }

    private Object method(WithReflectionAccess arg) {
        return arg;
    }

    public static void apply(Runnable runnable) {
        runnable.run();
    }

    private static class Inner {
    }
}
