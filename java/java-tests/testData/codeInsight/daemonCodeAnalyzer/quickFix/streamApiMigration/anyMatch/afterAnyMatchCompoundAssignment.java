// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "true-preview"

import java.util.List;

public class Main {
  public boolean testAnyMatch(List<String> data) {
    int x = 10;
      if (data.stream().map(String::trim).anyMatch(String::isEmpty)) {
          x *= 2;
      }
  }

}