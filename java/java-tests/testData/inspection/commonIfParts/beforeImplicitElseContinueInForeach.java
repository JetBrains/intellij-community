// "Collapse 'if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work(int i, List<String> texts) {
    for (String text : texts) {
      i--;
      if<caret>(i == 10) {
        System.out.println("Next iteration");
        continue;
      }
      System.out.println("Next iteration");
    }
  }
}