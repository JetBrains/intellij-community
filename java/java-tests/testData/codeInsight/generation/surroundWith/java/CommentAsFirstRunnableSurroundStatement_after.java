class Test {
    void foo() {
        <caret>Runnable runnable = new Runnable() {
            @Override
            public void run() {
                // This is comment"
                int i = 1;
            }
        };
    }
}