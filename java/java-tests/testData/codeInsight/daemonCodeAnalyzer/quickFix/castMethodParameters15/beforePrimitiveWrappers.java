// "Cast argument to 'long'" "true"
class a {
    void f(Long l) {}
    void g() {
        f(<caret>0);
    }
}

