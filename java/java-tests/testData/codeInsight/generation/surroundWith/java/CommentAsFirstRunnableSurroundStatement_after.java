class Test {
    void foo() {
        Runnable <caret><selection>runnable</selection> = new Runnable() {
            public void run() {
                // This is comment"
                int i = 1;
            }
        };
    }
}