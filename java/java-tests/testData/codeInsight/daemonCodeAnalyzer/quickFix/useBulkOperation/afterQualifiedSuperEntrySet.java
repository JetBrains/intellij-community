// "Replace iteration with bulk 'Map.putAll()' call" "true"
import java.util.HashMap;
import java.util.Map;

class Main extends HashMap<String, Integer> {
  class Nested {
    void test() {
      Map<String, Integer> result = new HashMap<>();
        result.putAll(Main.this);
    }
  }
}