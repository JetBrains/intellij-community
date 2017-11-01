// "Extract common part with variables from if " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX(int x) {
    return x;
  }

  int work() {
      final int y;
      if(true)  {
          y = getX(12);
      } else {
          y = getX(33);
      System.out.println(y);
      }
      return y;
  }
}