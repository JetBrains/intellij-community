// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found;
    if(Math.random() > 0.5) {
      found = true;
    } else {
        found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty());
    }
    System.out.println(found);
  }
}