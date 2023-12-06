import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void callTest() {
    String s = source();
    test(s);
  }

  @Tainted String source() {
    return "unsafe";
  }

  void test(String s) {
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}