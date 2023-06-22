import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = "safe";

  void callTest() {
    String s = field;
    test(s);
  }

  void test(@Untainted String s) {
    sink(s);
  }

  void sink(@Untainted String s) {}
}