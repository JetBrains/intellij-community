// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  String k;

  public void testMap(Map<String, List<String>> map, String value) {
      List<String> list = map.computeIfAbsent(this.k, k1 -> new ArrayList<>());
      list.add(value);
  }
}