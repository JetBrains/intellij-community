// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {
  private static final String NONE = "none";

  private String str;

  public String testGetOrDefault(Map<String, String> map, String key, Main other) {
    return map.conta<caret>insKey(key) ? map.get(key) : "oops";
  }
}