public class C {
    void both() {
        int x=0,y=0;
        a(x);b(y);
        a(y);b(x);
    }

    void first() {
        int k=0;
        a(k);b(1);
        a(1);b(k);
    }

    void second() {
        int m=1;
        <selection>a(0);b(m);
        a(m);b(0);</selection>
    }

    void baz(int e, int f) {
        a(e);b(f);
        a(f);b(e);
    }

    private void a(int i) {}
    private void b(int n) {}
}
