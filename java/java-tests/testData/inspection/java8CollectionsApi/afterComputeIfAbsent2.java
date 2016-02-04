// "Replace with 'computeIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    final java.lang.String k = "ads";
      map.computeIfAbsent(k, (s) -> {
          int i = s.length();
          return i;
      });
  }
}