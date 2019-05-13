// "Replace with collect" "false"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    System.out.println("hello");
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim());
      }
    }
    sb.append(sb.length());
    return sb.length() == 0 ? null : sb.toString();
  }
}