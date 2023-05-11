import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void test() {
    String s = foo();
    sink(<caret>s);
  }

  String foo() {
    return bar();
  }

  String bar() {
    return "safe";
  }

  void sink(@Untainted String s) {}
}