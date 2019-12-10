// "Replace 'Integer' with 'Number' in cast" "true"
import java.util.*;

class X {
  void test(Object x) {
    if (x instanceof Integer || x instanceof Long) {
      System.out.println(((<caret>Integer)x).longValue());
    }
  }
}