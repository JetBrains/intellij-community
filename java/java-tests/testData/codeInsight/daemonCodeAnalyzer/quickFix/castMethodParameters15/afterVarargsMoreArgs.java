// "Convert 1st argument to 'long'" "true-preview"
class a {
    void f(Long l, String... s) {}
    void g() {
        f(0L, "", "");
    }
}

