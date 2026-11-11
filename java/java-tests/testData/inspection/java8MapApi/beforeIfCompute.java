// "Replace with 'computeIfAbsent()' call" "true"
import java.util.*;

class Test{

  int k;

  void ensureExists(Map<String, List<String>> map, String key) {
    if<caret> (!map.containsKey(key)) {
      map.put(key, new ArrayList<>());
    }
  }

}