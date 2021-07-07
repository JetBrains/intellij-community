public class Test {
    public int test(int x) {
        int y = 42;
        try {
          y = y / x;
          foo();
        } catch (ArithmeticException  e) {
        }
        return y;
    }

    void foo(){ }
}