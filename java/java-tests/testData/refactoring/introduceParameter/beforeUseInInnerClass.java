class Test {
    public void test() {
        final String <caret>s = "";
        new Runnable() {
            public void run() {
                System.out.println(s);
            }
        }.run();
    }

    public void use() {
        test();
    }
}