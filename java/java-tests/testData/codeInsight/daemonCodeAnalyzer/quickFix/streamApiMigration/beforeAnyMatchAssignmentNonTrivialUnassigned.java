// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    if(Math.random() > 0.5) {
      System.out.println("oops");
    } else {
      for (String str : da<caret>ta) {
        String trimmed = str.trim();
        if (!trimmed.isEmpty()) {
          found = true;
          break;
        }
      }
    }
    System.out.println(found);
  }
}