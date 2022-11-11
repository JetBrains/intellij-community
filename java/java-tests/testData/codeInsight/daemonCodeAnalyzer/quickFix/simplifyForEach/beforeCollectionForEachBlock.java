// "Replace with collect" "true-preview"

import java.util.*;

public class Main {
  private void test(List<String> strs) {
    List<String> other = new ArrayList<>();
    strs.stream().forEac<caret>h(s -> {
      if(s.length() > 2) {
        other.add(s);
      }
    });
  }
}
