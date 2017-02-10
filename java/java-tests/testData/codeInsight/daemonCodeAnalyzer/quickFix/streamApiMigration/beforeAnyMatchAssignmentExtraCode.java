// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    if (data.size() > 10) {
      System.out.println("Big data");
    }
    for(String str : da<caret>ta) {
      String trimmed = str.trim();
      if(!trimmed.isEmpty()) {
        found = true;
        break;
      }
    }
    System.out.println(found);
  }
}