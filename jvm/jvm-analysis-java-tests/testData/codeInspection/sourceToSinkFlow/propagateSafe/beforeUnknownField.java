// "Propagate safe annotation from 's'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  String field = "safe";

  void test() {
    String s = field;
    sink(<caret>s);
  }
  
  void sink(@Untainted String s) {}
}