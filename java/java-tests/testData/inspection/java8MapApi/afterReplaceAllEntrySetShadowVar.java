// "Replace with 'replaceAll' method call" "true"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public void test() {
    String k = "another var";
    String key = "one more";
    Map<String, String> map = new HashMap<>();
    map.put("foo", "bar");
      map.replaceAll((k1, v) -> k);
  }
}