// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
      boolean found = data.stream().map(String::trim).allMatch(trimmed -> trimmed.isEmpty());
  }
}