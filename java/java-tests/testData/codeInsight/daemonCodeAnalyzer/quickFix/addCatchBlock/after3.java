// "Add Catch Clause(s)" "true"
class a {
    void g() throws Exception {
    }

    // initializer
    {
        try {
            // comment before
            g();
            // comment after
        } catch (Exception e) {
            <caret><selection>e.printStackTrace();</selection>
        }
    }
}
