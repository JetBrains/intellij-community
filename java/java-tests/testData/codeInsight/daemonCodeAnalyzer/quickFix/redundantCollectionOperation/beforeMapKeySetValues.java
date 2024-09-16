// "Fix all 'Redundant 'Collection' operation' problems in file" "true"
import java.util.*;

class Test {
  void test(Map<String, String> map) {
    if (map.ke<caret>ySet().isEmpty()) return;
    if (map.values().isEmpty()) return;
    map.keySet().remove("oops");
    map.values().remove("oops");
    if (map.keySet().size() > 10) return;
    if (map.values().size() > 10) return;
    map.keySet().clear();
    map.values().clear();
  }
}