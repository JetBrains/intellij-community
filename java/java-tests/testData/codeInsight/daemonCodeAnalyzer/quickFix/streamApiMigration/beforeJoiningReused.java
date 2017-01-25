// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuffer sb = new StringBuffer();
    if(!list.isEmpty()) {
      for (String s : li<caret>st) {
        if (!s.isEmpty()) {
          sb.append(s);
        }
      }
    }
    String s = sb.toString();
    return s.trim();
  }
}