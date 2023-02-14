class Test {
    public void method() {
        int temp = 1 + 2;
        Runnable r = new Runnable() {
            public void run() {
                int i = temp;
            }
        };
    }
}