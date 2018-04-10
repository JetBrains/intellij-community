// "Unroll loop" "true"
import java.util.*;

class Test {
  void test() {
    fo<caret>r(String s : List.of("foo")) {
      if(!s.isEmpty()) {
        System.out.println(s);
      }
    }
  }
}