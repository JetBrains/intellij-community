// "Replace with 'computeIfAbsent' method call" "true"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
      map.computeIfAbsent("ads", (s) -> {
          int i = 10;
          return i;
      });
  }
}