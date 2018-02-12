// "Replace with <>" "false"

import java.util.function.Consumer;

class Test {
    public static <T extends Comparable<T>> Foo<T> test(Consumer<? super T> function) {
        return new Foo<<caret>T>(function);
    }

    public static class Foo<T extends Comparable<T>> {
        Foo(Consumer<? super T> function) { }
    }

}