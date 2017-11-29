// "Replace with 'merge' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key) {
    if(map.get(key)<caret> == null) {
      map.put(key, 1)
    } else {
      map.put(key, map.get(key) + 1);
    }
  }
}