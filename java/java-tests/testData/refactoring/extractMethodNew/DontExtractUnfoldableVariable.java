class Test {
    void test(boolean condition){
        int x = 42;
        <selection>x = 55;
        if (condition) return;</selection>
        System.out.println(x);
    }
}