// "Replace iteration with bulk 'Map.putAll()' call" "true-preview"
import java.util.*;

class Main {
  void test(Map<String, Integer> map) {
    Map<String, Integer> result = new HashMap<>();
    result.put("answer", 42);
    for (Iterator<Map.Entry<String, Integer>> iterator = map.entrySet().iterator(); iterator.hasNext(); ) {
      Map.Entry<String, Integer> e = iterator.next();
      result<caret>.put(e.getKey(), e.getValue());
    }
  }
}
