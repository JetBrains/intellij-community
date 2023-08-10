import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  void callTest() {
    String s = foo();
    test(s);
  }

   String foo() {
    return "safe";
  }

  void test(@Untainted String s) {
    sink(s);
  }

  void sink(@Untainted String s) {}
}