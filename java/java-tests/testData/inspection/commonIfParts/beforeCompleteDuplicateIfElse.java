// "Collapse if statement " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  int work() {
    if (true) {} else if<caret>(true) {
      int x = getX();
    } else{
      int x = getX();
    }
  }
}