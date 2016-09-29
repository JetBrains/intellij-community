// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found = false;
    try {
        found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty());
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
    System.out.println(found);
  }
}