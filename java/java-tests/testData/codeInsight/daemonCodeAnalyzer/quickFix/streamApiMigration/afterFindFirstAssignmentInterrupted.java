// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    int firstSize;
    int other = map.size();
    if(other > 10) {
      System.out.println("Big");
    }
      // comment
      firstSize = map.values().stream().filter(Objects::nonNull).findFirst().map(List::size).orElse(0);
    System.out.println(firstSize);
  }
}