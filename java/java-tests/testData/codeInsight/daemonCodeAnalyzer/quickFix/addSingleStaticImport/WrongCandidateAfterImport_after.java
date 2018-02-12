package foo;

import static foo.Test.X.test;

class Test {

  public void foo() {
    test("bla");
  }

  static class Y {
    public void foo() {
      X.test("bla");
    }

    public void test(int x) {}
  }

  public static class X {
    public static void test(String x) {}
  }
}