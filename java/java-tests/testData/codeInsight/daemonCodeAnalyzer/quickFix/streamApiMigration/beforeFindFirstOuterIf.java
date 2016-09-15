// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public static String find(List<List<String>> list) {
    if(list != null) {
      for (List<String> innerList : lis<caret>t) {
        for (String string : innerList) {
          if (string.startsWith("ABC")) {
            return string;
          }
        }
      }
    }
    return null;
  }
}