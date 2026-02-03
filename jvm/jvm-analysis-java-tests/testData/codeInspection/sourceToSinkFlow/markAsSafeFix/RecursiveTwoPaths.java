import org.checkerframework.checker.tainting.qual.*;

class Simple {
  
  String field = "";

  void test(boolean b) {
    String s = b ? foo() : field;
    sink(<caret>s);
  }

  String foo() {
    return "";
  }


  void sink(@Untainted String s) {}
}