// "Replace with 'getOrDefault' method call" "false"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, Integer> map, String key) {
    System.out.println(map.<caret>containsKey(key) ? map.get(key) : 0.0);
  }
}