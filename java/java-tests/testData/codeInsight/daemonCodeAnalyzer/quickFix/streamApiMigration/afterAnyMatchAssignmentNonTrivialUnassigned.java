// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    if(Math.random() > 0.5) {
      System.out.println("oops");
    } else {
        found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty());
    }
    System.out.println(found);
  }
}