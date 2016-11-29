// "Replace with anyMatch()" "false"

import java.util.List;

public class Main {
  boolean find(List<String> data, String prefix) {
    if(prefix == null)
      prefix = "xyz";
    for(String e : da<caret>ta) {
      String trimmed = e.trim();
      if(trimmed.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
