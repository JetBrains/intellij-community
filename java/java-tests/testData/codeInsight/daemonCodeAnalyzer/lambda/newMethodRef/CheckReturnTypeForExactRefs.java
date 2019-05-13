class Test {

    interface A<X> {
        X m();
    }
    interface B<X> extends A<X> {}
    interface C<X> {}

    int integerRes() { return new Integer(42); }

    int intRes() { return 42; }

    void m(A<Integer> a) {}
    void m(B<String> b)  {}
    void m(C<CharSequence> b)  {}

    void test(boolean flag) {
        m(this::integerRes);
        m(flag ? this::integerRes : this::integerRes);

        m(this::intRes);
        m(flag ? this::intRes : this::intRes);
    }

}
