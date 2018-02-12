// "Replace with findFirst()" "true"

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public int[] testMap(Map<String, List<String>> map) throws Exception {
      int[] arr = map.values().stream().filter(Objects::nonNull).findFirst().map(list -> new int[]{list.size()}).orElse(null);
      return arr;
  }
}