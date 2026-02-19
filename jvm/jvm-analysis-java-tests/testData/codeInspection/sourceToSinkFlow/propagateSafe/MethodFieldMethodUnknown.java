import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = "safe";

  void test() {
    String s = foo();
    sink(<caret>s);
  }

  String foo() {
    return field;
  }

  void setFieldToBar() {
    this.field = bar();
  }

  String bar() {
    return "safe";
  }

  void sink(@Untainted String s) {}
}