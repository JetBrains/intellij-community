// "Replace cast expression with existing pattern variable 'i'" "true-preview"
import java.util.*;

class X {
  void test(Object obj) {
    if (obj instanceof Integer i) {
      doSomething(i);
    }
  }

  void doSomething(Integer i) {}
}