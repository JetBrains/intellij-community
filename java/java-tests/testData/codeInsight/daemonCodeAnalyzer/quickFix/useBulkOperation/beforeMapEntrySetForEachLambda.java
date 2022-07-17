// "Replace iteration with bulk 'Map.putAll()' call" "true"
import java.util.*;

class Main {
  void test(Map<String, Integer> map) {
    Map<String, Integer> result = new HashMap<>();
    result.put("answer", 42);
    map.entrySet().forEach(e -> result<caret>.put(e.getKey(), e.getValue()));
  }
}
