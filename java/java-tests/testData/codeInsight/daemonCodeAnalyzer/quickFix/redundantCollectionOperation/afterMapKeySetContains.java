// "Replace with 'Map.containsKey()'" "true"
import java.util.Map;

class Test {
  void test(Map<String, String> map, String key) {
    if(map.containsKey(key)) {
      System.out.println("contains");
    }
  }
}