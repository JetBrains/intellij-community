class Test {
    interface I {
        void m(Integer x1, Integer x2, Integer x3);
    }

    static class Foo {
       static void foo() {}
    }

    <T> void bar(I i) {}

    void test() {
        bar<error descr="'bar(Test.I)' in 'Test' cannot be applied to '(<method reference>)'">(Foo::foo)</error>;
    }
}