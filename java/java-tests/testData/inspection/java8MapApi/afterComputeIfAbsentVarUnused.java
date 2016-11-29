// "Replace with 'computeIfAbsent' method call" "true"
import java.util.Map;

public class Main {

  public void test(Map<String, List<String>> map, String key) {
    List<String> list = map.get(key);
      map.computeIfAbsent(key, k -> new ArrayList<>());
    System.out.println(map);
  }
}