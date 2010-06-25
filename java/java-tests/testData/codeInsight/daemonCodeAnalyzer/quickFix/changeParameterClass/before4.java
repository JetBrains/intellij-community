// "Make 'b' extend 'a'" "true"
class a {
    void f(a a) {
        b b = null;
        f(<caret>b);
    }
}
class b {
}

