// "Replace with 'putIfAbsent()' call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    <caret>if (!map.containsKey("ads")) {
      map.put("ads", i);
    }
  }
}