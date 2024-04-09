// "Replace cast expressions with pattern variable" "true"
import java.util.*;

class X {
  void test(Object obj) {
    if (obj instanceof Integer && ((Integer)obj).intValue() == 1) {
      doSomething((In<caret>t<caret>eger)obj);
    }
  }

  void doSomething(Integer i) {}
}