// "Replace with 'computeIfAbsent' method call" "true"
import java.util.*;

class Test{

  int k;

  void ensureExists(Map<String, List<String>> map, String key) {
      map.computeIfAbsent(key, k1 -> new ArrayList<>());
  }

}