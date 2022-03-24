// "Propagate safe annotation from 'a'" "true"
import org.checkerframework.checker.tainting.qual.*;

class Simple {

  String a(String str) {
    return str + "";
  }

  void test(@Untainted String input) {
    @Untainted String s;
    s = a<caret>(input);
  }
}