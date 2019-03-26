// "Replace with 'replaceAll' method call" "true"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();
    map = new HashMap<>();
    map.put("foo", "bar");
    for<caret> (String key : map.keySet()) {
      map.put(key, map.get(key));
    }
  }
}