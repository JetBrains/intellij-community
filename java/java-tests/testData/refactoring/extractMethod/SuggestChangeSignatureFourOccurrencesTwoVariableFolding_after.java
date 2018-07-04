public class C {
    void both() {
        int x=0,y=0;
        newMethod(y, x);
    }

    void first() {
        int k=0;
        newMethod(1, k);
    }

    void second() {
        int m=1;
        newMethod(m, 0);
    }

    private void newMethod(int m, int i) {
        a(i);
        b(m);
        a(m);
        b(i);
    }

    void baz(int e, int f) {
        newMethod(f, e);
    }

    private void a(int i) {}
    private void b(int n) {}
}
