// "Replace with 'putIfAbsent()' call" "false"
import java.util.Map;

class Test{

  List<String> ensureExists(Map<String, List<String>> map, String key) {
    if<caret> (!map.containsKey(key)) {
      return map.put(key, new ArrayList<>());
    } else {
      return map.get(key);
    }
  }

}