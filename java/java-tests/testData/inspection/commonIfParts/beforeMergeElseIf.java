// "Merge 'else if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  void work(int i, int j) {
    if(i != 0) {
    } else if<caret> (i/*1*/ < j/*2*/) {/*3*/
      int x = /*4*/ getX();
    } else if(/*5*/i >/*6*/ j/*7*/) {
      int x = getX(); // comments ignored
    } else {
      int y = 12;
    }
  }
}