class Test {
    interface I {
        Object _();
    }
    static class Foo<X> {}
    static class Foo1 {}
    void testAssign() {
        I o = <error descr="Raw constructor reference with explicit type parameters for constructor">Foo::<String>new</error>;
        I o1 = Foo1::<String>new;

    }
}

