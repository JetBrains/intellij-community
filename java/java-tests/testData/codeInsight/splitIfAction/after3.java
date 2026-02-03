class C {
    void foo() {
        if (<caret>a) {
            call();
        } else if (b) {
            call();
        }
    }
}