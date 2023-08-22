// "Use 'computeIfAbsent' method with functional argument" "true-preview"

import java.util.*;

class Test {
  public void test(Map<String, List<Integer>> map, String key) {
    map.computeIfAbsent(key, k -> new ArrayList<>());
    map.get(key).add(0);
  }
}