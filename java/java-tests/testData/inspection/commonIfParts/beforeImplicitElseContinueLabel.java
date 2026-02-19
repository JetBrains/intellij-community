// "Collapse 'if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  void work(int i) {
    loop:while(0 < i) {
      i--;
      if<caret>(i == 10)  {
        continue loop;
      }
      continue loop;
    }
  }
}