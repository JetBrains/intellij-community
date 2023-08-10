// "Collapse 'if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work() {
    for (int i = 0; i < 100; i++) {
      System.out.println("Another statement");
        System.out.println("Next iteration");
    }
  }
}