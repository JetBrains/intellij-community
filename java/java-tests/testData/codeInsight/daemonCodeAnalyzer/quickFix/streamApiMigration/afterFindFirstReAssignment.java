// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  private int getInitialSize() {return 0;}

  public void testMap(Map<String, List<String>> map) throws Exception {
    int firstSize = 10;

    System.out.println(firstSize);

      // loop
      // comment
      firstSize = map.values().stream().filter(Objects::nonNull).findFirst().map(List::size).orElse(getInitialSize());
    System.out.println(firstSize);
  }
}