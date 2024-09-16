// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(Map<String, String> map) {
    if (map.isEmpty()) return;
    if (map.isEmpty()) return;
    map.remove("oops");
    map.values().remove("oops");
    if (map.size() > 10) return;
    if (map.size() > 10) return;
    map.clear();
    map.clear();
  }
}