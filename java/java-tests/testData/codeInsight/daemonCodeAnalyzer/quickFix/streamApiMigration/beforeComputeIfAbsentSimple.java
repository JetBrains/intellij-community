// "Replace with collect" "true"

import java.util.*;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
    Map<Integer, List<String>> map = new HashMap<>();
    for(String s : l<caret>ist) {
      map.computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s);
    }
    return map;
  }
}
