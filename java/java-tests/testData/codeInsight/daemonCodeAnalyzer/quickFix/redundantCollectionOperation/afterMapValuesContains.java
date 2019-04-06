// "Replace with 'Map.containsValue()'" "true"
import java.util.Map;

class Test {
  void test(Map<String, String> map, String key) {
    if(map.containsValue(key)) {
      System.out.println("contains");
    }
  }
}