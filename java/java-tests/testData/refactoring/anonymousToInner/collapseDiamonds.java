public class Foo {
    public<T> void foo() {
        Predicate<T> predicate = ne<caret>w Predicate<T>() {
            @Override
            public boolean test(T t) {
                return false;
            }
        };
    }

    private interface Predicate<K> {
        boolean test(K t);
    }
}