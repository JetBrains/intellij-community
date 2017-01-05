// "Replace with findFirst()" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class Main {
  public void testMap(Map<String, List<String>> map) throws Exception {
    List<String> firstList = new ArrayList<>();
    firstList.add("none");
      firstList = map.values().stream().filter(Objects::nonNull).findFirst().orElse(firstList);
    System.out.println(firstList);
  }
}