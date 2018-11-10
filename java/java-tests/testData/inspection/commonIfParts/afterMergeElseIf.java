// "Merge 'else if' statement" "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  int getX() {
    return 42;
  }

  void work(int i, int j) {
      /*5*/
      /*7*/
      if(i != 0) {
    } else if (i/*1*/ < j || i >/*6*/ j/*2*/) {/*3*/
      int x = /*4*/ getX();
    } else {
      int y = 12;
    }
  }
}