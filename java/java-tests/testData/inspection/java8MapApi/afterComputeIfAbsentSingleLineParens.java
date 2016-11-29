// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map, String key, String value) {
      List<String> list = map.computeIfAbsent(key, k -> new ArrayList<>());
      list.add(value);
  }
}