// "Replace with collect" "true"

import java.util.Arrays;
import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    for<caret> (String s : list) {
      if (!s.isEmpty()) {
        sb.append(s.length()).append('"');
      }
    }
    if (sb.length() > 0) {
      sb.setLength(sb.length() - 1);
    }
    return sb.toString().trim();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("abc", "", "xyz", "argh")));
  }
}