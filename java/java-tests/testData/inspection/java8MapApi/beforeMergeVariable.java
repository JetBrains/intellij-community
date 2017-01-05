// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key) {
    Integer val = map.get(key);
    if(val<caret> == null) {
      map.put(key, 1)
    } else {
      map.put(key, 1 + val);
    }
  }
}