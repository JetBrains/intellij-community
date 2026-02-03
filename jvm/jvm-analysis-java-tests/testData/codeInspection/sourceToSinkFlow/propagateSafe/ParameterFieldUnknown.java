import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = "safe";

  void callTest() {
    String s = field;
    test(s);
  }

  void test(String s) {
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}