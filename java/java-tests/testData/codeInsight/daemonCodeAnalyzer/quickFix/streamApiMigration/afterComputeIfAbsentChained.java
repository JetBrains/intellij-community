// "Replace with forEach" "true"

import java.util.*;

public class Main {
  private Map<Integer, Map<Integer, List<String>>> test(String... list) {
    Map<Integer, Map<Integer, List<String>>> map = new HashMap<>();
      Arrays.stream(list).filter(Objects::nonNull).forEach(s -> map.computeIfAbsent(s.length(), k -> new HashMap<>()).computeIfAbsent(s.length(), k -> new ArrayList<>()).add(s));
    return map;
  }
}
