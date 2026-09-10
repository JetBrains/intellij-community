// "Replace with 'computeIfAbsent' method call" "false"
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map, String key) throws IOException {
    List<String> list = map.<caret>get(key);
    if (!map.containsKey(key)) {
      list = loadList();
      map.put(key, list);
      return;
    }
    return;
  }

  private List<String> loadList() throws IOException {
    throw new IOException();
  }
}
