// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.ArrayList;
import java.util.List;

public class Main {
  private List<String> test(String[] list, int limit) {
    List<String> result = new ArrayList<>();
    List<String> other = new ArrayList<>();
    System.out.println("hello");
    for(String s : li<caret>st) {
      if (s == null) {
        continue;
      }
      result.add(s+s);
      if(result.size() != limit) {
        continue;
      }
      break;
    }
    result.sort(null);
    return result;
  }
}
