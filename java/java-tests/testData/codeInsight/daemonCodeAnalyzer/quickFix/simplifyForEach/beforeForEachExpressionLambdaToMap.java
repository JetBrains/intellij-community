// "Replace with 'collect()'" "true-preview"

import java.util.*;

public class Main {
  private void test() {
    List<String> other = new ArrayList<>();
    HashMap<Integer, String> map = new HashMap<Integer, String>();
    other.stream().forEac<caret>h(s -> map.putIfAbsent(s.length(), s));
  }
}
