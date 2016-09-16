// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {
  private static final String NONE = "none";

  private String str;

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
    str = map.get(key);
    if(str == nul<caret>l) {
      /*
      block comment
       */
      str = NONE;
    }
    System.out.println(str);
  }
}