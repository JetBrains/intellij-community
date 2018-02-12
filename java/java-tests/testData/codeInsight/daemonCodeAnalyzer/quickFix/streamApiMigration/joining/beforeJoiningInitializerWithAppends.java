// "Replace with collect" "true"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder().append("1").append(2);
    System.out.println("hello");
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim());
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}