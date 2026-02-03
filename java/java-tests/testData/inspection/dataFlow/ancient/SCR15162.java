class NullTest {
    int m;
    void f() {};
    public void x() {
        NullTest t = null;
        <warning descr="Dereference of 't' will produce 'NullPointerException'">t</warning>.m = 12;
        t.f();
    }
}