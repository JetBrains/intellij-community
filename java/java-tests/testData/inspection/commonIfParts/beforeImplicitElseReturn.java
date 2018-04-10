// "Collapse if statement " "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work() {
    if<caret>(true)  {
      return;
    }
  }
}