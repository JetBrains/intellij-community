// "Unroll loop" "true"
import java.util.*;

class Test {
  void test() {
    fo<caret>r(String s : Arrays.asList("foo", "bar", "baz", "")) {
      if(!s.isEmpty()) {
        System.out.println(s);
      }
    }
  }
}