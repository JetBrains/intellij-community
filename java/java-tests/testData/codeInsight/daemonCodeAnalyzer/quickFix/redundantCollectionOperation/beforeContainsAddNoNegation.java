// "Remove the 'contains' check" "false"
import java.util.*;

class Test {
  void test(Set<String> set, String key) {
    if(set.co<caret>ntains(key)) {
      set.add(key);
    }
  }
}