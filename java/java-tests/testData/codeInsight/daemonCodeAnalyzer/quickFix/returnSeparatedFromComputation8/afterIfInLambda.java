// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    interface I {
        int call();
    }
    void f(boolean b) {
        g(() -> {
            int n = -1;
            if (b) return 1;
            return n;
        });
    }

    void g(I i) {
        i.call();
    }
}