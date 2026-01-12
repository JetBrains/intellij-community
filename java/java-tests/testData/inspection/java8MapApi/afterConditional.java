// "Replace with 'putIfAbsent()' call" "true"
import java.util.Map;
class Test {
  void m1111(Map<String, Integer> map) {
    int i = map.putIfAbsent("asd", 123);
  }
}