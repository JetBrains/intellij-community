// "Use 'computeIfAbsent' method with functional argument" "true"

import java.util.Map;

class Test {
  public void test(Map<String, List<Integer>> map, String key) {
    map.computeIfAbsent(key, k -> new ArrayList<>());
    map.get(key).add(0);
  }
}