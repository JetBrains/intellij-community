// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "true-preview"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = true;
    for(String str : da<caret>ta) {
      String trimmed = str.trim();
      if(!trimmed.isEmpty()) {
        found = false;
        break;
      }
    }
  }
}