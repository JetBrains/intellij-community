// "Replace with anyMatch()" "true"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found;
    try {
        found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty());
      System.out.println(found);
    }
    catch(Exception ex) {
      ex.printStackTrace();
    }
  }
}