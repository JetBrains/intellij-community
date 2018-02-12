class Main2  {

    <R> void bar(Fun<Integer, R> collector) { }

    <T, D> Fun<T, Integer> foo(D d) { return null; }

    public void test() {
        bar(new Foo<>());
    }

    interface Fun<T, R> {
        R f(T t);
    }

    class Foo<K> implements Fun<K, Integer> {
        @Override
        public Integer f(K k) {
            return null;
        }
    }
}
