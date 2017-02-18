// "Replace with allMatch()" "true"

import java.util.List;

public class Main {
  boolean find(List<String> data, boolean other, boolean third) {
    for(String e : da<caret>ta) {
      String trimmed = e.trim();
      if(!trimmed.startsWith("xyz")) {
        return false;
      }
    }
    return other || third;
  }
}
