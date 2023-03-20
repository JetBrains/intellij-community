// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "true-preview"

import java.util.List;

public class Main {
  public void testAssignment(List<String> data) {
    String found = data.stream().map(String::trim).anyMatch(trimmed -> !trimmed.isEmpty()) ? "yes" : "no";
  }
}