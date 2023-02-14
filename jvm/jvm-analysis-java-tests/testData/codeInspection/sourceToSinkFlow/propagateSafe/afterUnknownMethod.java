// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = foo();

  void test() {
    String s = foo();
    sink(s);
  }

    @Untainted String foo() {
    return "safe";
  }

  void sink(@Untainted String s) {}
}