// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    System.out.println("hello");
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s);
      }
    }
    sb.append("xyz");
    return sb.toString();
  }
}