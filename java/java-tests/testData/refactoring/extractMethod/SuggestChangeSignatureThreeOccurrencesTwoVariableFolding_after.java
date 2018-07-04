public class C {
    void foo() {
        int x=0,y=0;
        newMethod(x, y);
    }

    private void newMethod(int x, int y) {
        a(x);
        b(y);
        a(y);
        b(x);
    }

    void bar() {
        int k=0,m=1;
        newMethod(k, m);
    }

    void baz(int e, int f) {
        newMethod(e, f);
    }

    private void a(int i) {}
    private void b(int n) {}
}
