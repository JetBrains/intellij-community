// "Replace with collect" "false"

import java.util.List;

public class Test {
  static String test(List<String> list) {
    StringBuilder sb = new StringBuilder();
    System.out.println("hello");
    Runnable r = () -> {
      for(String s : li<caret>st) {
        if(!s.isEmpty()) {
          sb.append(s.trim());
        }
      }
    }
    return sb.length() == 0 ? null : sb.toString();
  }
}