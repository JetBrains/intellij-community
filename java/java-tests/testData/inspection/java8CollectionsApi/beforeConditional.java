// "Replace with 'putIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1111(Map<String, Integer> map) {
    int i = !map.containsKey("asd") ? map.<caret>put("asd", 123) : map.get("asd");
  }
}