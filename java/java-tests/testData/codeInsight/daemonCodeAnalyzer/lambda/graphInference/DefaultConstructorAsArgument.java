class Main2  {

    <R> void bar(Fun<Integer, R> collector) { }

    <T, D> Fun<T, Integer> foo(D d) { return null; }

    public void test() {
        bar(new Foo<>());
    }

    interface Fun<T, R> {
        R _(T t);
    }
    
    class Foo<K> implements Fun<K, Integer> {
        @Override
        public Integer _(K k) {
            return null;
        }
    }
}
