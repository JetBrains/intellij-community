class C {
    void foo() {
        //comment
        if (a /*inside*/ || c) {
            call();
        } else if (b) {
            call();
        }
    }
}