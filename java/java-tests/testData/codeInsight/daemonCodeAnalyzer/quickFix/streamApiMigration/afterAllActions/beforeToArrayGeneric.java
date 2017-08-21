// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public List<?>[] testToArray(List<String> data) {
    Set<List<String>> result = new LinkedHashSet<>();
    for (String str : dat<caret>a) {
      if (!str.isEmpty()) {
        List<String> list = Collections.singletonList(str);
        result.add(list);
      }
    }
    return result.toArray(new List<?>[0]);
  }
}