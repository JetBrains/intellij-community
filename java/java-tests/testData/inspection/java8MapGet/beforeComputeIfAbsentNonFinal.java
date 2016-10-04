// "Replace with 'computeIfAbsent' method call" "false"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map, String key, String value) {
    int size = 5;
    if(map.isEmpty()) size = 10;
    List<String> list = map.get(key);
    if(list == <caret>null) {
      list = new ArrayList<>(size);
      map.put(key, list);
    }
    list.add(value);
  }
}