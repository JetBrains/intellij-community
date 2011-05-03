class Test {
    void foo() {
        synchronized (<caret>) {
            // This is comment"
            int i = 1;
        }
    }
}