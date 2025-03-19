// "Convert argument to 'long'" "true-preview"
class a {
    void f(Long l) {}
    void g() {
        f(<caret>0);
    }
}

