import org.checkerframework.checker.tainting.qual.*;

class Simple {

  void test(@Untainted String s) {
    sink(s);
  }

  void sink(@Untainted String s) {}
}