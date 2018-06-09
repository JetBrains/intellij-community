import java.util.*;

class Test {
  interface IntFunction {
    int m(int i);
  }

  private final int idx;

  private final int idx2 = Test.this.idx + 1;

  private final IntFunction multiply = i -> i * Test.this.idx;

  public Test(int idx) {
    this.idx = idx;
  }
}
class C {
  interface Simplest {
    void m();
  }
  void use(Simplest s) { }

  interface IntParser {
    int parse(String s);
  }

  void test() {
    Simplest simplest = () -> { };
    use(() -> { });

    IntParser intParser = (String s) -> Integer.parseInt(s);
  }

  Runnable foo() {
    return () -> { System.out.println("foo"); };
  }
}