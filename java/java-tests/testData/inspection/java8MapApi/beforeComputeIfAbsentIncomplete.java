// "Replace with 'computeIfAbsent' method call" "false"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map, String key, String value) {
    List<String> list = map.get(key);
    if(list == nul<caret>l) {
    }
    list.add(value);
  }
}