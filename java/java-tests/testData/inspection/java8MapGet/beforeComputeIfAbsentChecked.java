// "Replace with 'computeIfAbsent' method call" "false"
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Main {
  static class MyList extends ArrayList<String> {
    public MyList() throws Exception {

    }
  }

  public void testMap(Map<String, List<String>> map, String key, String value) throws Exception {
    List<String> list = map.get(key);
    if(list == nul<caret>l) {
      list = new MyList();
      map.put(key, list);
    }
    list.add(value);
  }
}