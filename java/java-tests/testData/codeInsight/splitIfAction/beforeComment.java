class C {
    void foo() {
        if (a |<caret>| //comment
            b) {
            call();
        }
    }
}