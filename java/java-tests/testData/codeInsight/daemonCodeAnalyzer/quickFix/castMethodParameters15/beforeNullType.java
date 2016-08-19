// "Cast parameter to 'java.lang.String'" "true"
class a {
    void f(Long... l) {}
    void f(String s) {}
    void g() {
        f(<caret>null);
    }
}

