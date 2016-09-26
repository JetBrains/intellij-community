// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    if(Math.random() > 0.5) {
      found = true;
    } else {
        if (data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty())) {
            found = true;
        }
    }
  }
}