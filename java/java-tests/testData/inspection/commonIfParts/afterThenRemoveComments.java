// "Extract variables from if " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  int work() {
      int x = getX();
      if(true)  {
          //Then
    } else {
          return x;
    }
  }
}