public class Test {
    public int test(int x) {
        int y = 42;
        try {
          <selection>foo();
          y = y / x;</selection>
        } catch (ArithmeticException  e) {
        }
        return y;
    }

    void foo() {}
}