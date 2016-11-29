// "Replace with 'putIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    if (!map.contai<caret>nsKey("ads")) {
      map.put("ads", i);
    }
  }
}