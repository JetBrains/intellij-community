// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
      int bigSize = map.values().stream().mapToInt(List::size).filter(size -> size > 10).findFirst().orElse(0);
      System.out.println(bigSize);
  }
}