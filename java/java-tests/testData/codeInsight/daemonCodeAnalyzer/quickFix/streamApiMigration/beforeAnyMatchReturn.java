// "Replace with anyMatch()" "true"

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
      return true;
    }
    return false;
  }

}