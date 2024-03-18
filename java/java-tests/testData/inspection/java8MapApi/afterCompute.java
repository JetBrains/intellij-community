// "Replace with 'compute' method call" "true"
import java.util.Map;

public class Main {
  public void testCompute(Map<String, Integer> map, String key) {
      map.compute(key, (k, value) -> value == null ? 0 : value + 1);
  }
}