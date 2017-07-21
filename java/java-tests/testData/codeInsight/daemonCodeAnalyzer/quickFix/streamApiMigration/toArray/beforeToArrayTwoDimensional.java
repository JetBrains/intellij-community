// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public String[][] testToArray(List<String> data) {
    List<String[]> result = new ArrayList<>();
    for (String str : dat<caret>a) {
      if (!str.isEmpty()) {
        String[] arr = {str};
        result.add(arr);
      }
    }
    return result.toArray(new String[result.size()][]);
  }
}