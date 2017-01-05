// "Replace with 'computeIfAbsent' method call" "true"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  String k;

  public void testMap(Map<String, List<String>> map, String value) {
    List<String> list = map.get(this.k);
    if(list == nul<caret>l) {
      list = new ArrayList<>();
      map.put(this.k, list);
    }
    list.add(value);
  }
}