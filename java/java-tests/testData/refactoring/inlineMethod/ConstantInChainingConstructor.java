class Test {
    public static final int ID=0;

    public <caret>Test() {
        this(ID);
    }

    public Test(int id) {
    }
}

class Rest {
    public static void test() {
        new Test();
    }
}