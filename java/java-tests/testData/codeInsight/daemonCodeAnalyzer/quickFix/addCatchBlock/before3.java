// "Add 'catch' clause(s)" "true-preview"
class a {
    void g() throws Exception {
    }

   // initializer
    {
        try {
           // comment before
            <caret>g();
           // comment after
        }
    }
}
