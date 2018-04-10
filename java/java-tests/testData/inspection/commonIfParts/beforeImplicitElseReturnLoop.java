// "Collapse if statement " "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work() {
    int x = 1;
    while(true) {
      if<caret> (x > 12) {
        return;
      }
      x++;
    }
  }
}