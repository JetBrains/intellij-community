// "Replace iteration with bulk 'Map.putAll()' call" "true-preview"
import java.util.*;

class Main {
  void test(Map<String, Integer> map) {
    Map<String, Integer> result = new HashMap<>();
    result.put("answer", 42);
    map.forEach(result::put<caret>);
  }
}
