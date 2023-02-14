// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  String field = baz();

  void test(boolean b) {
    String s = b ? foo() : field;
    sink(<caret>s);
  }

  String foo() {
    return bar();
  }

  String bar() {
    return baz();
  }
  
  String baz() {
    return foo();
  }

  void sink(@Untainted String s) {}
}