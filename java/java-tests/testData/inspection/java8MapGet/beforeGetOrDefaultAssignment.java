// "Replace with 'getOrDefault' method call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, String> map, String key) {
    Integer num = 123;
    System.out.println(num);
    num = map.get(key);
    if(num == nu<caret>ll) num = 0;
    System.out.println(num);
  }
}