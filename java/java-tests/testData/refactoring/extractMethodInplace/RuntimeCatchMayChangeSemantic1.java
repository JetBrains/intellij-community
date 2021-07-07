public class Test {
    public int test(int x) {
        int y = 42;
        try {
          y = y + x;
          y = y / x;
        } catch (ArithmeticException  e) {
        }
        return y;
    }
}