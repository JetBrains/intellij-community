// "Replace with 'replaceAll' method call" "false"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public void test() {
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    for<caret> (Map.Entry<String, String> entry : map.entrySet()) {
      map.put(entry.getKey(), map.toString());
    }
  }
}