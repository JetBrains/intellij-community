class Test {
    static int offset = 42;

    void test(){
        int avgA = <selection>10 + 20 / 2 - Test.offset - 1</selection>;
        int avgB = 100 + 200 / 2 - Test.offset - 1;
    }
}