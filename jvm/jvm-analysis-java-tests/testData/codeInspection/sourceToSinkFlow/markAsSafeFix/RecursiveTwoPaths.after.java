import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted
    String field = "";

  void test(boolean b) {
    String s = b ? foo() : field;
    sink(s);
  }

    @Untainted
    String foo() {
    return "";
  }


  void sink(@Untainted String s) {}
}