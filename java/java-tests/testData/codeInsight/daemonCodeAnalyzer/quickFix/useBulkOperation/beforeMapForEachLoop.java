// "Replace iteration with bulk 'Map.putAll()' call" "true"
import java.util.*;

class Main {
  void test(Map<String, Integer> map) {
    Map<String, Integer> result = new HashMap<>();
    result.put("answer", 42);
    for (Map.Entry<String, Integer> e : map.entrySet()) {
      result<caret>.put(e.getKey(), e.getValue());
    }
  }
}
