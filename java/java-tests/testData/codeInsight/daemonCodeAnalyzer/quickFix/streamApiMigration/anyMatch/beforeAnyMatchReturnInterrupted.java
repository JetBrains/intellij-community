// "Collapse loop with stream 'anyMatch()/noneMatch()/allMatch()'" "false"

import java.util.List;

public class Main {
  public boolean testAnyMatch(List<List<String>> data) {
    if (!data.isEmpty()) {
      for (List<String> list : dat<caret>a) {
        for (String str : list) {
          if (!str.isEmpty()) {
            System.out.println("Found!");
            return true;
          }
        }
      }
      System.out.println("Oops");
      return true;
    }
    return false;
  }

}