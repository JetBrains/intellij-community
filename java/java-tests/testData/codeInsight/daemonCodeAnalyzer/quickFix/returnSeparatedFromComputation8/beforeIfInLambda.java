// "Move 'return' closer to computation of the value of 'n'" "true-preview"
class T {
    interface I {
        int call();
    }
    void f(boolean b) {
        g(() -> {
            int n = -1;
            if (b) n = 1;
            ret<caret>urn n;
        });
    }

    void g(I i) {
        i.call();
    }
}