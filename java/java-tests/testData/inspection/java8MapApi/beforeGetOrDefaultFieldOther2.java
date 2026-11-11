// "Replace with 'getOrDefault()' call" "false"
import java.util.Map;

public class Main {
  private String str;

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
    str = map.get(key);
    if<caret>(str == null) {
      // comment
      other.str = "";
    }
    System.out.println(str);
  }
}