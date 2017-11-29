// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    boolean first = true;
    for(String s : li<caret>st) {
      sb.append(", ").append(s.trim());
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}