class Test {
    void foo() {
        <caret>Runnable runnable = new Runnable() {
            public void run() {
                // This is comment"
                int i = 1;
            }
        };
    }
}