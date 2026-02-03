import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void test() {
    String s = foo();
    sink(<caret>s);
  }

  String foo() {
    return source();
  }

  @Tainted String source() {
    return "unsafe";
  }

  void sink(@Untainted String s) {}
}