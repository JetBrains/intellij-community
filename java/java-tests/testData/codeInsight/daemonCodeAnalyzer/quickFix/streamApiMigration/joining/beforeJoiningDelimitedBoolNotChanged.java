// "Collapse loop with stream 'collect()'" "false"

import java.util.Arrays;
import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    if (!list.isEmpty()) {
      for (String s : li<caret>st) {
        if (!s.isEmpty()) {
          if (first) {
            sb.append(s.length());
          } else {
            sb.append('"').append(s.length());
          }
        }
      }
    }
    return sb.toString().trim();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("abc", "", "xyz", "argh")));
  }
}