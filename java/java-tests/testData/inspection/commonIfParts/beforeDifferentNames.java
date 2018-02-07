// "Collapse if statement " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  int work() {
    if<caret>(true)  {
      int y = getX();
      return y;
    } else {
      int x = getX();
      return x;
    }
  }
}