// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(Map<String, String> map) {
    if (map.isEmpty()) return;
    if (map.isEmpty()) return;
    if (map.isEmpty()) return;
    map.remove("oops");
    map.values().remove("oops");
    map.entrySet().remove("oops");
    if (map.size() > 10) return;
    if (map.size() > 10) return;
    if (map.size() > 10) return;
    map.clear();
    map.clear();
    map.clear();
  }
  
  static abstract class MyMap extends AbstractMap<String, String> {
    public int size() {
      return keySet().size();
    }

    abstract public Set<K> keySet();
  }
}