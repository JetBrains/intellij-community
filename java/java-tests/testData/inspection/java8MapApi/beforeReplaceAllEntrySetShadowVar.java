// "Replace with 'replaceAll' method call" "true"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public void test() {
    String k = "another var";
    String key = "one more";
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
    for<caret> (Map.Entry<String, String> entry : map.entrySet()) {
      map.put(entry.getKey(), k);
    }
  }
}