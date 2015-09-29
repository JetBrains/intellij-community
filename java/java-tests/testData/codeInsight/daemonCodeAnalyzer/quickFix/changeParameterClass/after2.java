// "Make 'a' extend 'b'" "true"
class a extends b <caret>{
    void f(b b, Runnable r) {
        f(this, null);
    }
}
class b {
}

