// "Propagate safe annotation from 'a'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

    @Untainted String a(@Untainted String str) {
    return str + "";
  }

  void test(@Untainted String input) {
    @Untainted String s;
    s = a(input);
  }
}