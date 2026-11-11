// "Replace with 'putIfAbsent()' call" "true"
import java.util.Map;

class Test{

  Integer m2(Map<String, Integer> map) {
    <caret>if (!map.containsKey("asd")) {
      return map.put("asd", 123);
    } else {
      return map.get("asd");
    }
  }

}