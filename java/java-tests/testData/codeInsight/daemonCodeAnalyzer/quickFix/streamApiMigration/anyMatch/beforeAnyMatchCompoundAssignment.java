// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public boolean testAnyMatch(List<String> data) {
    int x = 10;
    for(String str : da<caret>ta) {
      String trimmed = str.trim();
      if(trimmed.isEmpty()) {
        x *= 2;
        break;
      }
    }
  }

}