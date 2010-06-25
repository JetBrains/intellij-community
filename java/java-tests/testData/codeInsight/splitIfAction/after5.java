class C {
    void foo() {
        if (<caret>a && b) {
            if (c && d && e) {
                call();
            }
        }
    }
}