public class Test {
    public int test(int x) {
        int y = 42;
        try {
            y = getY(x, y);
        } catch (ArithmeticException  e) {
        }
        return y;
    }

    private int getY(int x, int y) {
        foo();
        y = y / x;
        return y;
    }

    void foo() {}
}