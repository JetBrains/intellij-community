// "Unroll loop" "true"
import java.util.*;

class Test {
  void test() {
    fo<caret>r(String s : Collections.singleton("xyz")) {
      if(!s.isEmpty()) {
        System.out.println(s);
      }
    }
  }
}