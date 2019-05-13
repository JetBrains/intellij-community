// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
      if (data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty())) {
          System.out.println("Found!!!");
      }
  }
}