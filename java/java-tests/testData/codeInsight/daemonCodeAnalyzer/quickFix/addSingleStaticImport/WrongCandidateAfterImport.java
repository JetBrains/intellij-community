package foo;

class Test {

  public void foo() {
    X.te<caret>st("bla");
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