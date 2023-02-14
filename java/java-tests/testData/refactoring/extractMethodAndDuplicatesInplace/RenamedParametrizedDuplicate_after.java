class Test {
    static int offset = 42;

    void test(){
        int avgA = averageWithOffset(10, 20);
        int avgB = averageWithOffset(100, 200);
    }

    private static int averageWithOffset(int x, int x1) {
        return x + x1 / 2 - Test.offset - 1;
    }
}