// "Replace with collect" "false"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    // Not supported for now
    StringBuilder sb = new StringBuilder("initial");
    for (String s : li<caret>st) {
      if (!s.isEmpty()) {
        sb.append(s);
      }
    }
    return sb.toString().trim();
  }
}