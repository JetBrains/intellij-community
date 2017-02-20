// "Replace with 'putIfAbsent' method call" "false"
import java.util.Map;

class Test{

  List<String> ensureExists(Map<String, List<String>> map, String key) {
    if (!map.conta<caret>insKey(key)) {
      return map.put(key, new ArrayList<>());
    } else {
      return map.get(key);
    }
  }

}