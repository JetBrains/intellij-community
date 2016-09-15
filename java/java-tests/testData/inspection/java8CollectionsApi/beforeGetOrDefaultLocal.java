// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, String> map, String key, Main other) {
    String a = null, str = map.get(k<caret>ey);
    if(str == null) {
      // comment
      str = "";
    }
    System.out.println(str);
  }
}