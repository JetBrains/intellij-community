// "Replace with 'merge' method call" "false"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key, int add) {
    Integer val = map.get(key);
    if(val<caret> == null) {
      map.put(key, add + val);
    } else {
      map.put(key, add)
    }
    System.out.println(val);
  }
}