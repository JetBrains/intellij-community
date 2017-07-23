// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder("initial");
    for (String s : li<caret>st) {
      if (!s.isEmpty()) {
        sb.append(s);
      }
    }
    return sb.toString().trim();
  }
}