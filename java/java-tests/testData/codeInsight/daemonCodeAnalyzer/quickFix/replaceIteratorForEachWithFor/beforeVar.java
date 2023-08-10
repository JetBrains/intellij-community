// "Replace 'for each' loop with iterator 'for' loop" "true-preview"
import java.util.Iterator;

class X {
  void test(Iterator<?> it) {
    for (var x : i<caret>t) {
      System.out.println(x);
    }
  }
}