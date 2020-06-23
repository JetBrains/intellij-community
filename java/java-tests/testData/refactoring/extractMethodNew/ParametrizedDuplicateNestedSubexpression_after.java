class C {
    public void foo(C c, long d, int i, String s) {
        newMethod(d, s, 1, i());
        newMethod(d, s, i, k(d));
        c.m(d, i);
    }

    private void newMethod(long d, String s, int i, int i2) {
        b(s, i).m(d, i2);
    }

    private C b(String s, int i) { return new C(); }
    void m(long d, int i) { }

    private int i() { return 0; }
    private int k(long d) { return 0; }
}