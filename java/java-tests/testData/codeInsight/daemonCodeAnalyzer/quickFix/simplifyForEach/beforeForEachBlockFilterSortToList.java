// "Replace with 'collect()'" "true-preview"

import java.util.*;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
    List<String> other = new ArrayList<>();
    other.stream().forEac<caret>h(s -> {
      if(s.length() > 2) {
        strs.add(s);
      }
    });
    strs.sort(String::compareTo);
  }
}
