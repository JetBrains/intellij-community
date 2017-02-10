// "Replace with findFirst()" "true"

import java.util.*;

public class Main {
  public Integer[] testFindFirstIfPresent(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    for (List<String> list : data) {
        list.stream().filter(str -> !str.isEmpty()).findFirst().ifPresent(str -> result.add(str.length()));
    }
    return result.toArray(new Integer[result.size()]);
  }
}