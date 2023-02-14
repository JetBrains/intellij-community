// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void test() {
    String s = foo();
    sink(s);
  }

    @Untainted String foo() {
    return bar();
  }

    @Untainted String bar() {
    return "safe";
  }

  void sink(@Untainted String s) {}
}