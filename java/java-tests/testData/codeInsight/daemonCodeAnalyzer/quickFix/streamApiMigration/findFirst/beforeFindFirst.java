// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.List;

public class Main {
  public static String find(List<List<String>> list) {
    for(List<String> innerList : li<caret>st) {
      for(String string : innerList) {
        if(string.startsWith("ABC")) {
          return string.substring(3);
          /*
          Block comment
           */
        }
      }
    }
    return "";
  }
}