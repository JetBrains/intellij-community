// "Change type of 'sub' to 'Sub' and remove cast" "false"
public class Outer {
  void test() {
    Super sub = getSub();
    sub.foo();
    ((<caret>Sub) sub).bar();
  }

  Sub getSub() { return new Sub(); }

  static class Super {
    private void foo() {}
  }

  static class Sub extends Super {
    void bar() {}
  }
}
