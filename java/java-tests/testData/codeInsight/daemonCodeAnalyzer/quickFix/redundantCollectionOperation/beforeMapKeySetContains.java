// "Replace with 'Map.containsKey()'" "true"
import java.util.Map;

class Test {
  void test(Map<String, String> map, String key) {
    if(map.keySet().cont<caret>ains(key)) {
      System.out.println("contains");
    }
  }
}