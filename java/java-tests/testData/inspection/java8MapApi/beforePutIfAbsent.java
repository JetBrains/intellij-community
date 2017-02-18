// "Replace with 'putIfAbsent' method call" "true"

import java.util.HashMap;
import java.util.Map;

public class Main {
  public static void main(String[] args) {
    Map<String, String> vals = new HashMap<>();
    String v = vals.get("foo");
    if(v <caret>== null) {
      vals.put("foo", "bar");
    }
    System.out.println(v);
  }
}
