// "Replace with collect" "false"

import java.util.*;

public class Main {
  private Set<String> test(String[] list, int limit) {
    Set<String> result = new HashSet<>();
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
