// "Replace with findFirst()" "true-preview"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public List<Integer> testFindFirstIfPresent(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    if (!data.isEmpty()) {
      for (List<String> list : da<caret>ta) {
        for (String str : list) {
          if (!str.isEmpty()) {
            result.add(str.length());
            return result;
          }
        }
      }
    }
    return result;
  }

}