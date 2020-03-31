// "Use 'computeIfAbsent' method with functional argument" "false"

import java.util.*;

class Test {
  public void test(Map<String, List<Integer>> map, String key) {
    map.putIfAbsent(key, createList<caret>());
    map.get(key).add(0);
  }

  private native List<Integer> createList();
}