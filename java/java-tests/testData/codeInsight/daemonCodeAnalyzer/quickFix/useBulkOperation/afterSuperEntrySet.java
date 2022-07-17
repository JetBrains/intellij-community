// "Replace iteration with bulk 'Map.putAll()' call" "true"
import java.util.HashMap;
import java.util.Map;

class Main extends HashMap<String, Integer> {
  void test() {
    Map<String, Integer> result = new HashMap<>();
      result.putAll(this);
  }
}