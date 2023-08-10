import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = foo();

  void test() {
    String s = foo();
    sink(<caret>s);
  }

  String foo() {
    return "safe";
  }

  void sink(@Untainted String s) {}
}