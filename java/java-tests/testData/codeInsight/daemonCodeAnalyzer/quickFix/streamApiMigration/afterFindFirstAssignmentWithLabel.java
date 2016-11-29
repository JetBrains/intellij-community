// "Replace with findFirst()" "true"

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
      String firstStr = map.values().stream().filter(Objects::nonNull).flatMap(Collection::stream).filter(str -> !str.isEmpty()).findFirst().orElse("");
      System.out.println(firstStr);
  }
}