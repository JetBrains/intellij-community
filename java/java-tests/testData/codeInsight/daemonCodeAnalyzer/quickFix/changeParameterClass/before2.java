// "Make 'a' extend 'b'" "true"
class a {
    void f(b b, Runnable r) {
        f(<caret>this, null);
    }
}
class b {
}

