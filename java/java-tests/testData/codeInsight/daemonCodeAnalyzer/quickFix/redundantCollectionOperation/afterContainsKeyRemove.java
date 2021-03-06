// "Remove 'containsKey()' check" "true"
import java.util.Map;

class Test {
  void test(Map<String, Integer> map, String key) {
      /*contains!!!*/
      map.remove(/*remove!!!*/key);
  }
}