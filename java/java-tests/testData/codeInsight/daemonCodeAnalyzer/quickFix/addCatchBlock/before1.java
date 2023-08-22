// "Add 'catch' clause(s)" "true-preview"
class a {
    void g() throws Exception {
    }
    void f() {
        try {
            <caret>g();
        } catch (Error e) {
        }
    }
}
