class Test {
    void foo() {
        while (<caret><selection>true</selection>) {
            // This is comment"
            int i = 1;
        }
    }
}