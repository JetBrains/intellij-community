class Test {
    public void method() {
        1 + 2 <caret>
        Runnable r = new Runnable() {
            public void run() {
                int i = 1 + 2;
            }
        };
    }
}