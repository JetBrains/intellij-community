class Class1 {
    public static int staticMethod() {
        int a = 1;
        int b = 2;

        int temp = a + b;
        return temp * 2;
    }

    public int foo(int a, int b) {
        <selection>int temp = a + b;
        return temp * 2;</selection>
    }
}