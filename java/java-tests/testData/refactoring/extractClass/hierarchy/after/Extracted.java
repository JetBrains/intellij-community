public class Extracted<R> {
    private final Test<R> test;
    public R myT;

    public Extracted(Test<R> test) {
        this.test = test;
    }

    void bar() {
        test.foo(myT);
    }
}