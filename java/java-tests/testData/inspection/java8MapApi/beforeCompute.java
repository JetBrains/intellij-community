// "Replace with 'compute' method call" "true"
import java.util.Map;

public class Main {
  public void testCompute(Map<String, Integer> map, String key) {
    Integer value = map.get(key);
    map.pu<caret>t(key, value == null ? 0 : value + 1);
  }
}