// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(Map<String, String> map) {
    if (map.ke<caret>ySet().isEmpty()) return;
    if (map.values().isEmpty()) return;
    if (map.entrySet().isEmpty()) return;
    map.keySet().remove("oops");
    map.values().remove("oops");
    map.entrySet().remove("oops");
    if (map.keySet().size() > 10) return;
    if (map.values().size() > 10) return;
    if (map.entrySet().size() > 10) return;
    map.keySet().clear();
    map.values().clear();
    map.entrySet().clear();
  }
  
  static abstract class MyMap extends AbstractMap<String, String> {
    public int size() {
      return keySet().size();
    }

    abstract public Set<K> keySet();
  }
}