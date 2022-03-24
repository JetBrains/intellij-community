// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted String field = baz();

  void test(boolean b) {
    String s = b ? foo() : field;
    sink(s);
  }

    @Untainted String foo() {
    return bar();
  }

    @Untainted String bar() {
    return baz();
  }

    @Untainted String baz() {
    return foo();
  }

  void sink(@Untainted String s) {}
}