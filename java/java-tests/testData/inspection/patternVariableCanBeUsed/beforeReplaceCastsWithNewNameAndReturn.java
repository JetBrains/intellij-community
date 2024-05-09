// "Replace cast expressions with pattern variable" "true"
import java.util.*;

class X {
  void test(Object obj) {
    if (!(obj instanceof Integer)) {
      return;
    }
    doSomething((In<caret>t<caret>eger)obj);
  }

  void doSomething(Integer i) {}
}