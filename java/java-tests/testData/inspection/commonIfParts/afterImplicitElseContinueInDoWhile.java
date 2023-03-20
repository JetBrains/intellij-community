// "Collapse 'if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work(int i) {
    do {
      i--;
        System.out.println("Next iteration");
    } while(0 < i);
  }
}