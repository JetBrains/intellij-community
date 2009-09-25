public class NullTest {
    int m;
    void f() {};
    public void x() {
        NullTest t = null;
        t.m = 12;
        t.f();
    }
}