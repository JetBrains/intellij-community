// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  String field = source();

  void test() {
    String s = field;
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}

  @Tainted String source() {
    return "unsafe";
  }
}