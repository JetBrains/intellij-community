// "Replace with 'remove()' call" "true"
import java.util.Map;

public class Main {
  void test(Map<String, String> map, String key, String value) {
    if (value.<caret>equals(map.get(key))) map.remove(key);
  }
}
