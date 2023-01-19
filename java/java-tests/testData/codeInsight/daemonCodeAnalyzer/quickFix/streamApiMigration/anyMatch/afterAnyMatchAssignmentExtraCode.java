// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "true-preview"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    boolean found;
    if (data.size() > 10) {
      System.out.println("Big data");
    }
      found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty());
    System.out.println(found);
  }
}