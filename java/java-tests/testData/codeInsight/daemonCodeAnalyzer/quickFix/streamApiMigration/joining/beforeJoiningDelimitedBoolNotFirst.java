// "Replace with collect" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    boolean notFirst = false;
    if (!list.isEmpty()) {
      for (String s : li<caret>st) {
        if (!s.isEmpty()) {
          if (!notFirst) {
            sb.append(s.length());
            notFirst = true;
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