// "Move 'return' closer to computation of the value of 'n'" "true"
class T {
    interface I {
        int call();
    }
    void f(boolean b) {
        g(() -> {
            if (b) return 1;
            return -1;
        });
    }

    void g(I i) {
        i.call();
    }
}