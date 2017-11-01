// "Collapse if statement " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int work() {
    if<caret>(true)  {
      return 12;
    }
    return 12;
  }
}