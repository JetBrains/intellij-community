// "Extract common part with variables from if " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX(int x) {
    return x;
  }

  int work() {
    if<caret>(true)  {
      final int y = getX(12);
      return y;
    } else {
      final int x = getX(33);
      System.out.println(x);
      return x;
    }
  }
}