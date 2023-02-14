// "Propagate safe annotation from 'a'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted String a(@Untainted String str) {
    return str + "";
  }

  void test() {
    @Untainted String s;
    s = a(foo());
  }

  @Untainted
  String foo() {
    return "safe";
  }
}