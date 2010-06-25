// "Cast parameter to 'a'" "true"
class x {}
class a extends x {
    a(a a) {}
    a(x x) {}

    void f(Runnable r) {
        new a(<caret>r);
    }
}

