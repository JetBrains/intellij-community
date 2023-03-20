// "Collapse 'if' statement" "false"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work(int i) {
    outer:while(0 < i) {
      i--;
      while(i % 2 == 0)  {
        i--;
        if<caret>(i == 10)  {
          System.out.println("Next iteration");
          continue outer;
        }
        System.out.println("Next iteration");
      }
    }
  }
}