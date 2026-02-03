public class C {
    void foo() {
        int x=0,y=0;
        <selection>a(x);b(y);
        a(y);b(x);</selection>
    }

    void bar() {
        int k=0,m=1;
        a(k);b(m);
        a(m);b(k);
    }

    void baz(int e, int f) {
        a(e);b(f);
        a(f);b(e);
    }

    private void a(int i) {}
    private void b(int n) {}
}
