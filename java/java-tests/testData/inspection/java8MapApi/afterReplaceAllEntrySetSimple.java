// "Replace with 'replaceAll' method call" "true"

import java.util.Map;

public class Main {
  public static void main(String[] args) {
    Map<String, String> vals = new HashMap<>();
    vals.put("foo", "bar");
    String defaultValue = "42";
      vals.replaceAll((k, v) -> defaultValue);
  }
}
