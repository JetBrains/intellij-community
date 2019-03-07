// "Replace with 'merge' method call" "true"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key) {
      map.merge(key, 1, Integer::sum)
  }
}