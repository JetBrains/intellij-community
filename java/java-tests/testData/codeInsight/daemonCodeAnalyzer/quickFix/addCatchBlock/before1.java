// "Add Catch Clause(s)" "true"
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
