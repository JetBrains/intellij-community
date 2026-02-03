import org.checkerframework.checker.tainting.qual.*;

class Simple {

  @Tainted String field = "safe";

  void test() {
    String s = foo();
    sink(<caret>s);
  }

  String foo() {
    return field;
  }

  void sink(@Untainted String s) {}
}