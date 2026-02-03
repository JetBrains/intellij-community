import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void test(String s) {
    sink(<caret>s);
  }

  void sink(@Untainted String s) {}
}