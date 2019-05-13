class C {
    void foo() {
        if (a /*inside*/ || c |<caret>| //comment
            b) {
            call();
        }
    }
}