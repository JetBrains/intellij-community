// "Replace with findFirst()" "false"

import java.util.*;

// The result will have block-lambda so we don't suggest the replacement here
public class Main {
  public Integer[] testFindFirstIfPresent(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    for (List<String> list : data) {
      for (String str : lis<caret>t) {
        if (!str.isEmpty()) {
          result.add(str.length());
          System.out.println(str);
          break;
        }
      }
    }
    return result.toArray(new Integer[result.size()]);
  }
}