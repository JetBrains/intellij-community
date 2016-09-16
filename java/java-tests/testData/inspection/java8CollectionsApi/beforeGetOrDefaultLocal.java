// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
    String a = null, str = map.get(key);
    if(str == nu<caret>ll) {
      // comment
      str = "";
    }
    System.out.println(str);
  }
}