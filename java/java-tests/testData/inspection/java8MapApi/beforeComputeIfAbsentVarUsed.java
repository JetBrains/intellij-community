// "Replace with 'computeIfAbsent' method call" "false"
import java.util.Map;

public class Main {

  public void test(Map<String, List<String>> map, String key) {
    List<String> list = map.get(key);
    if (list == <caret>null) {
      map.put(key, new ArrayList<>());
    }
    System.out.println(list);
  }
}