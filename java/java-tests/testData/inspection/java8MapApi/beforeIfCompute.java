// "Replace with 'computeIfAbsent' method call" "true"
import java.util.*;

class Test{

  int k;

  void ensureExists(Map<String, List<String>> map, String key) {
    if (!map.conta<caret>insKey(key)) {
      map.put(key, new ArrayList<>());
    }
  }

}