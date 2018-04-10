// "Use 'computeIfAbsent' method with functional argument" "false"

import java.util.Map;

class Test {
  public void test(Map<String, List<Integer>> map, String key) {
    List<Integer> old = map.putIfAbsent(key, new ArrayL<caret>ist<>());
    map.get(key).add(0);
  }
}