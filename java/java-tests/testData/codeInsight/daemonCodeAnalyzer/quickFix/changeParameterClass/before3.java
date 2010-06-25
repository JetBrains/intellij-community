// "Make 'a' extend 'b'" "false"
class a extends Object {
    void f(b b, Runnable r) {
        f(<caret>this, null);
    }
}
class b {
}

