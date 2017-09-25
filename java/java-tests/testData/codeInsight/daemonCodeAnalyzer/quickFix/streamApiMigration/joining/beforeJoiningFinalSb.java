// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    final StringBuilder sb = new StringBuilder("ctor");
    sb.append("first")
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim());
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}