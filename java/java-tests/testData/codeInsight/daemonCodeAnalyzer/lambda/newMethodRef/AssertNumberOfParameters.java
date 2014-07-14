class Test {
    interface I {
        void m(Integer x1, Integer x2, Integer x3);
    }

    static class Foo {
       static void foo() {}
    }

    <T> void bar(I i) {}

    void test() {
        bar(Foo::<error descr="Cannot resolve method 'foo'">foo</error>);
    }
}