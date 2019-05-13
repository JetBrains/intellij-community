class Pos05 {

    static class Foo<X> {
        Foo(X x) {}
    }

    void m(Foo<Integer> fi) {}

    void test() {
        m(new Foo<>(1));
    }
}
