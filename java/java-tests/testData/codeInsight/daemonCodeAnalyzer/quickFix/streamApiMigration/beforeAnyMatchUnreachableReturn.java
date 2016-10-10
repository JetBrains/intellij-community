// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  boolean find(List<String> data) {
    if(data != null) {
      for (String e : da<caret>ta) {
        String trimmed = e.trim();
        if (trimmed.startsWith("xyz")) {
          return true;
        }
      }
    } else {
      throw new IllegalArgumentException();
    }
    return false;
  }
}
