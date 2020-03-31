class ErrorTest {
    public static <E1 extends Throwable> void rethrow(Thrower<? extends E1> thrower) {
        try {
            thrower.doThrow();
        }
        catch (Throwable e) {
            <error descr="Unhandled exception: E1">throw e;</error>
        }
    }
    interface Thrower<E extends Throwable> {
        void doThrow() throws E;
    }
}