// "Make 'b' extend 'a'" "false"
class a {
    void f(a a, b b) {
        b b = null;
        f(<caret>b, b);
    }
}
interface b {
}

