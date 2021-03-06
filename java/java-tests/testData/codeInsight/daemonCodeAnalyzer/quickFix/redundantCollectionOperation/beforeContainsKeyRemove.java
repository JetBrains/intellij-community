// "Remove 'containsKey()' check" "true"
import java.util.Map;

class Test {
  void test(Map<String, Integer> map, String key) {
    if(map.co<caret>ntainsKey(/*contains!!!*/key)) {
      map.remove(/*remove!!!*/key);
    }
  }
}