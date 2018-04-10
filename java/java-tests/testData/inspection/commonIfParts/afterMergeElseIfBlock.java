// "Merge else if statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  void work() {
    if (true || false) {
      int x = getX();
    } else {
      int y = 12;
    }
  }
}