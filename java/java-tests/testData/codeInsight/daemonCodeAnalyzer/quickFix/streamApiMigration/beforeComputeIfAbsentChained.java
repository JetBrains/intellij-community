// "Replace with forEach" "true"

import java.util.*;

public class Main {
  private Map<Integer, Map<Integer, List<String>>> test(String... list) {
    Map<Integer, Map<Integer, List<String>>> map = new HashMap<>();
    for(String s : li<caret>st) {
      if(s != null) {
        map.computeIfAbsent(s.length(), k -> new HashMap<>()).computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s);
      }
    }
    return map;
  }
}
