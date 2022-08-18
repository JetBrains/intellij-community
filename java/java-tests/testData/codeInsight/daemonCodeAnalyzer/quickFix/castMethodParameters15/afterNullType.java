// "Cast argument to 'String'" "true-preview"
class a {
    void f(Long... l) {}
    void f(String s) {}
    void g() {
        f((String) null);
    }
}

