// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  void callTest() {
    String s = foo();
    test(s);
  }

    @Untainted String foo() {
    return "safe";
  }

  void test(@Untainted String s) {
    sink(s);
  }

  void sink(@Untainted String s) {}
}