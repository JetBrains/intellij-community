// "Cast parameter to 'x'" "true"
class x {}
class a extends x {
    a(a a) {}
    a(x x) {}

    void f(Runnable r) {
        new a(<caret>(x) r);
    }
}

