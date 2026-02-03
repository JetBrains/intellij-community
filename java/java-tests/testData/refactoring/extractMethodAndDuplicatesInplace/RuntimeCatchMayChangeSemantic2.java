public class Test {
    public int test(int x) {
        int y = 42;
        try {
          <selection>y = y / x;
          foo();</selection>
        } catch (ArithmeticException  e) {
        }
        return y;
    }

    void foo(){ }
}