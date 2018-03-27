// "Extract common part removing branch " "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  int work() {
    if(true) <caret> {
      final int y = getX();
      return y;
    } else {
      int x = getX();
      System.out.println(x);
      return x;
    }
  }
}