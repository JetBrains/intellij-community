// "Make 'a' implement 'java.lang.Runnable'" "true"
class a {
    void f(Runnable r) {
        f(<caret>this);
    }
}

