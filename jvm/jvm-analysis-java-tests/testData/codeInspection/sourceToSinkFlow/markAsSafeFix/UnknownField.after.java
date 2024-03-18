import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted
    String field = "safe";

  void test() {
    String s = field;
    sink(s);
  }
  
  void sink(@Untainted String s) {}
}