import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String field = foo();

  void test() {
    String s = source();
    sink(<caret>s);
  }
  
  @Tainted String source() {
    return "unsafe";
  }

  void sink(@Untainted String s) {}
}