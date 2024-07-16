// "Replace cast expressions with pattern variable" "true"
import java.util.*;

class X {
  void test(Object obj) {
    if (!(obj instanceof Integer integer)) {
      return;
    }
    doSomething(integer);
  }

  void doSomething(Integer i) {}
}