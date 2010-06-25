// "Cast 1st parameter to 'double'" "true"
class x {}
class a extends x {
    a(a a) {}
    a(x x) {}
    a(int i, double d) {}
    a(double d, int i) {}

    void f(Runnable r) {
        new a(<caret>1, 1);
    }
}

