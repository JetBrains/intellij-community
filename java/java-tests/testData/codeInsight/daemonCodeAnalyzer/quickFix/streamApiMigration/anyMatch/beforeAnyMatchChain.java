// "Collapse loop with stream 'anyMatch()'" "true-preview"

import java.util.List;

public class Main {
  boolean find(List<String> data, boolean other, boolean third) {
    for(String e : da<caret>ta) {
      String trimmed = e.trim();
      if(trimmed.startsWith("xyz")) {
        return true;
      }
    }
    return other || third;
  }
}
