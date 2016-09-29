// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    try {
      for (String str : da<caret>ta) {
        String trimmed = str.trim();
        if (!trimmed.isEmpty()) {
          found = true;
          break;
        }
      }
      System.out.println(found);
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }
}