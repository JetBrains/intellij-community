// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list, StringBuilder sb) {
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim());
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}