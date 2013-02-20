class Outer {

    private interface Inner {
        void m();
    }

    void m(Inner i) {}
}

class Usage {
    void test(Outer outer) {
        outer.m(<error descr="'Outer.Inner' has private access in 'Outer'">() -> {}</error>);
        outer.m(<error descr="'Outer.Inner' has private access in 'Outer'">this::foo</error>);
    }

    void foo() {}
}