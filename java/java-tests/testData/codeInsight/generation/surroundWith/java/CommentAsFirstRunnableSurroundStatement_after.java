class Test {
    void foo() {
        Runnable <caret>runnable = new Runnable() {
            public void run() {
                // This is comment"
                int i = 1;
            }
        };
    }
}