// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    sb.append("asdsad").append("bar");
    for(String s : li<caret>st) {
      if(!s.isEmpty()) {
        sb.append(s.trim());
      }
    }
    return sb.length() == 0 ? null : sb.toString//comment
      ();
  }
}