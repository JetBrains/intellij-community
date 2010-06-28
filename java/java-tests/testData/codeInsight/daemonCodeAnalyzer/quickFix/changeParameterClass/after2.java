// "Make 'a' extend 'b'" "true"
<caret>class a extends b {
    void f(b b, Runnable r) {
        f(this, null);
    }
}
class b {
}

