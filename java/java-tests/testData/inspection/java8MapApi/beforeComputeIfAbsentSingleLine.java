// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map, String key, String value) {
    List<String> list = map.get(key);
    if(list == nul<caret>l) {
      map.put(key, list = new ArrayList<>());
    }
    list.add(value);
  }
}