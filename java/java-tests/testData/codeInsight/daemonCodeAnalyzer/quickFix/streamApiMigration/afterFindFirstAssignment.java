// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
      int firstSize = map.values().stream().filter(Objects::nonNull).findFirst().map(List::size).orElse(0);
      // comment
      System.out.println(firstSize);
  }
}