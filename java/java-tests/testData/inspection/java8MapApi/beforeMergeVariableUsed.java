// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key, int add) {
    Integer val = map.get(key);
    if(val<caret> == null) {
      map.put(key, add)
    } else {
      map.put(key, add + val);
    }
    System.out.println(val);
  }
}