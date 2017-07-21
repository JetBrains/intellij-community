// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<Integer> list) {
    StringBuffer sb = new StringBuffer();
    if(!list.isEmpty()) {
      for (Integer i : li<caret>st) {
        if (i != 0) {
          sb.append(i);
        }
      }
    }
    String s = sb.toString();
    return s.trim();
  }
}