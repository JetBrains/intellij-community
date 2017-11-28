// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder("[");
    for (String s : li<caret>st) {
      if (!s.isEmpty()) {
        if(sb.length() > 1) sb.append(',');
        sb.append(s);
      }
    }
    return sb.append("]").toString().trim();
  }
}