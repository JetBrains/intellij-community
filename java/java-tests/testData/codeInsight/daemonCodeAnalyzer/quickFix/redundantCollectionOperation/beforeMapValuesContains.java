// "Replace with 'Map.containsValue()'" "true-preview"
import java.util.Map;

class Test {
  void test(Map<String, String> map, String key) {
    if(map.values().cont<caret>ains(key)) {
      System.out.println("contains");
    }
  }
}