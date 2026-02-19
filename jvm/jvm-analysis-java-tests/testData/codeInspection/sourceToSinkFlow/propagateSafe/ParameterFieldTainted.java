import org.checkerframework.checker.tainting.qual.*;

class Simple {

  @Tainted String field = "unsafe";

  void callTest() {
    String s = field;
    test(s);
  }

  void test(String s) {
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}