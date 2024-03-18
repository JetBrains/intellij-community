// "Replace with 'replaceAll' method call" "false"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();

    map.put("foo", "bar");
    fo<caret>r (String key : map.keySet()) {
      map.put(key, map == null ? "1" : map.get(key));
    }
  }
}