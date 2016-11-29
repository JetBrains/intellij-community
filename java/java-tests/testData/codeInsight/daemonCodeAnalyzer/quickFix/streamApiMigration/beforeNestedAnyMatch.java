// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public Integer[] testNestedAnyMatch(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    for (List<String> list : d<caret>ata) {
      for (String str : list) {
        if (!str.isEmpty()) {
          result.add(list.size());
          break;
        }
      }
    }
    return result.toArray(new Integer[result.size()]);
  }
}