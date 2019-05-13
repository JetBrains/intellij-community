// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    int firstSize = 0, other = firstSize;
      // comment
      firstSize = map.values().stream().filter(Objects::nonNull).findFirst().map(List::size).orElse(firstSize);
    System.out.println(firstSize);
  }
}