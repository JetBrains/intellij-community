// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "true-preview"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    for (String str : d<caret>ata) {
      String trimmed = str.trim();
      if (!trimmed.isEmpty()) {
        System.out.println("Found!!!");
        break;
      }
    }
  }
}