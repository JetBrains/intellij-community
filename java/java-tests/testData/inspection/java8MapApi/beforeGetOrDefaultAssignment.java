// "Replace with 'getOrDefault()' call" "true"
import java.util.Map;

public class Main {

  public void testGetOrDefault(Map<String, Integer> map, String key) {
    Integer num = 123;
    System.out.println(num);
    num = map.get(key);
    if<caret>(num == null) num = 0;
    System.out.println(num);
  }
}