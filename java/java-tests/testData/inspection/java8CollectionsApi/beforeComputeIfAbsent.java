// "Replace with 'computeIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    if (!map.contai<caret>nsKey("ads")) {
      int i = 10;
      map.put("ads", i);
    }
  }
}