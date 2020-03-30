class C {
    public void foo(C c, long d, int i, String s) {
        <selection>b(s, 1).m(d, i())</selection>;
        b(s, i).m(d, k(d));
        c.m(d, i);
    }

    private C b(String s, int i) { return new C(); }
    void m(long d, int i) { }

    private int i() { return 0; }
    private int k(long d) { return 0; }
}