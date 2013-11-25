class Test {

    interface A {}
    interface B {}
    static interface C extends A, B {}
    static interface D extends A, B {}

    interface I<T, V> {
        V fun(T arg);
    }
    <Z> Z m(Z z) { return z; }

    void test(C c, D d) {
        choose(c, d, x -> x);
        choose(c, d, this::m);
    }

    <T> void choose(T t1, T t2, I<T, T> t3) {}
}