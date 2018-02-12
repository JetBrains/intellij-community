// "Collapse 'if' statement and extract side effect" "true"

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
    A a3 = new A();
      if (a1 == null || !sideEffects(a1) || !sideEffects(a2)) {
          sideEffects(a3);
      }
      return 12;
  }
}