// "Add 'catch' clause(s)" "true"
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
