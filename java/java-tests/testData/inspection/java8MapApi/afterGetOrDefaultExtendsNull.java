// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, ? extends Number> map, String key) {
    System.out.println(map.getOrDefault(key, null));
  }
}