// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    System.out.println("hello");
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim()).append(12 + "asd" + s).append(1);
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}