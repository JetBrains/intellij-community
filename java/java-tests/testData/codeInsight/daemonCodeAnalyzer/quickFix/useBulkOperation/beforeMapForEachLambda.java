// "Replace iteration with bulk 'Map.putAll()' call" "true"
import java.util.*;

class Main {
  void test(Map<String, Integer> map) {
    Map<String, Integer> result = new HashMap<>();
    result.put("answer", 42);
    map.forEach((key, value) -> result<caret>.put(key, value));
  }
}
