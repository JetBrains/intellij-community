// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted String field = "safe";

  void test() {
    String s = foo();
    sink(s);
  }

    @Untainted String foo() {
    return field;
  }

  void sink(@Untainted String s) {}
}