// "Unroll loop" "true-preview"
import java.util.*;

class Test {
  void test() {
    fo<caret>r(String s : List.of("foo", "bar", "baz", "")) {
      if(!s.isEmpty()) {
        System.out.println(s);
      }
    }
  }
}