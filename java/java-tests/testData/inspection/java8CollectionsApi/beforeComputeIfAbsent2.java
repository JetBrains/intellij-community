// "Replace with 'computeIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    final java.lang.String k = "ads";
    if (!map.contai<caret>nsKey(k)) {
      int i = k.length();
      map.put(k, i);
    }
  }
}