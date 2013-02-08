class Main  {

    <R> void bar(Fun<Integer, R> collector) { }

    <T, D> Fun<T, Integer> foo(D d) { return null; }

    public void test() {
        bar(foo(""));
    }

    interface Fun<T, R> {
        R _(T t);
    }
}