// "Collapse if statement " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int work() {
    if (true) {
      if<caret> (false) {
        System.out.println();
        return;
      }
    }
    System.out.println();
  }
}