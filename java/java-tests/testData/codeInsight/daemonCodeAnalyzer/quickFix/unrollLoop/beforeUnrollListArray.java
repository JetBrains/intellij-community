// "Unroll loop" "false"
import java.util.*;

class Test {
  void test(String[] data) {
    fo<caret>r(String s : Arrays.asList(data)) {
      if(!s.isEmpty()) {
        System.out.println(s);
      }
    }
  }
}