import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.function.Function;

class Main {
    public static void main(String[] args) {
        runTest(Main::test, UncheckedIOException:: new);
    }

    private static void test() throws IOException {}

    private static <E extends Throwable> void runTest(A<? extends E> a, Function<E, ?> b) { }

    @FunctionalInterface
    public interface A<E extends Throwable> {
        void foo() throws E;
    }

}