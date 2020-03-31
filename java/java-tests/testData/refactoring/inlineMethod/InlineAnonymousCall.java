public class Test {
  static class X {
    void foo() {
      System.out.println(this);
    }
  }

  void test() {
    new X () {
      void bar() {
        <caret>foo();
      }
    };
  }
}