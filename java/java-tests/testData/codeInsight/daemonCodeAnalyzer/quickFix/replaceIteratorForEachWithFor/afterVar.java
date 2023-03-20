// "Replace 'for each' loop with iterator 'for' loop" "true-preview"
import java.util.Iterator;

class X {
  void test(Iterator<?> it) {
      for (Iterator<?> iter = it; iter.hasNext(); ) {
          var x = iter.next();
          System.out.println(x);
      }
  }
}