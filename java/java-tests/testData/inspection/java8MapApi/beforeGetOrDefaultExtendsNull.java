// "Replace with 'getOrDefault()' call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, ? extends Number> map, String key) {
    System.out.println(map.containsKey(key) <caret>? map.get(key) : null);
  }
}