// "Collapse 'if' statement" "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work() {
    for (int i = 0; i < 100; i++) {
      if<caret>(i == 10) {
        System.out.println("Next iteration");
        continue;
      }
      System.out.println("Next iteration");
      System.out.println("Another statement");
    }
  }
}