class Test {
    public void test(final String anObject) {
        new Runnable() {
            public void run() {
                System.out.println(anObject);
            }
        }.run();
    }

    public void use() {
        test("");
    }
}