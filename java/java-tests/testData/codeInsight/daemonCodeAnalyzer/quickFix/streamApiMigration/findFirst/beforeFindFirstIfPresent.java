// "Replace with findFirst()" "true"

import java.util.*;

public class Main {
  public Integer[] testFindFirstIfPresent(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    for (List<String> list : data) {
      for (String str : lis<caret>t) {
        if (!str.isEmpty()) {
          result.add(str.length());
          break;
        }
      }
    }
    return result.toArray(new Integer[result.size()]);
  }
}