// "Replace with 'merge' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.Map;

public class Main {
  public void testMerge(Map<String, Integer> map, String key) {
      map.merge(key, 1, (a, b) -> a + b)
  }
}