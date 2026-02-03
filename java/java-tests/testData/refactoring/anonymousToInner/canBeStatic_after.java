public class Foo {
    public<T> void foo() {
        Predicate<T> predicate = new MyPredicate<>();
    }

    private interface Predicate<K> {
        boolean test(K t);
    }

    private static class MyPredicate<T> implements Predicate<T> {
        @Override
        public boolean test(T t) {
            return false;
        }
    }
}