// "Add 'catch' clause(s)" "true"
class a {
    void g() throws Exception {
    }
    void f() {
        try {
            g();
        } catch (Error e) {
        } catch (Exception e) {
            <caret><selection>throw new RuntimeException(e);</selection>
        }
    }
}
