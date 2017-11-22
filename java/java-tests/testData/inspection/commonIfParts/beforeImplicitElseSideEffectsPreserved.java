// "Collapse if statement " "true"

import java.util.Collection;
import java.util.List;
import java.util.Set;

public class IfStatementWithIdenticalBranches {
  static class A {
    int x = 1;
  }

  boolean sideEffects(A a) {
    a.x = 12;
  }

  int work() {
    A a1 = new A();
    A a2 = new A();
    if<caret>(sideEffects/*intruder1*/(a1)) { // c1
      if<caret>(sideEffects(a2)) {
        return /*intruder2*/ 12; //c2
      }
    }
    return 12;
  }
}