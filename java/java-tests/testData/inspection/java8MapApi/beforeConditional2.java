// "Replace with 'putIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1111(Map<String, Integer> map) {
    int i = map.cont<caret>ainsKey("asd") ? map.get("asd") : map.put("asd", 123);
  }
}