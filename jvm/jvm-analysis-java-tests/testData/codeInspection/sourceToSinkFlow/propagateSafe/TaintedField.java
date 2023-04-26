import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  @Tainted String field = "unsafe";

  void test() {
    String s = field;
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}