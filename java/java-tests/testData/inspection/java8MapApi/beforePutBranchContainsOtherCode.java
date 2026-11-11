// "Replace with 'putIfAbsent()' call" "false"
import java.util.Map;
class Test {
  void m1(Map<String, Integer> map) {
    if<caret> (!map.containsKey("ads")) {
      int i = 10;
      map.put("ads", i);
    }
  }
}