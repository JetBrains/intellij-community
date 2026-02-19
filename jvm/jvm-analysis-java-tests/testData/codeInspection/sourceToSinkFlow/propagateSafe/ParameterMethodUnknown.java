import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  void callTest() {
    String s = foo();
    test(s);
  }

   String foo() {
    return "safe";
  }

  void test(String s) {
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}