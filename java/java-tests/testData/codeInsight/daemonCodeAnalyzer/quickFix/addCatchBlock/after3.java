// "Add 'catch' clause(s)" "true-preview"
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
            <caret><selection>throw new RuntimeException(e);</selection>
        }
    }
}
