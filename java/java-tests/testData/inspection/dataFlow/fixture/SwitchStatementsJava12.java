public class SwitchStatementsJava12 {
  void testMultiLabel(int x) {
    if(x > 0) {
      switch (x) {
        case 1, 2, <warning descr="Switch label '-1' is unreachable">-1</warning>:
          System.out.println("oops");
          break;
      }
      switch (x) {
        case 1, 2, <warning descr="Switch label '-1' is unreachable">-1</warning> -> {
          System.out.println("oops");
          break;
        }
      }
    }
  }
  
  void testThrowRule(int x) {
    switch (x) {
      case 0 -> throw new IllegalArgumentException();
      default -> System.out.println(<warning descr="Condition 'x == 0' is always 'false'">x == 0</warning>);
    }
    if (<warning descr="Condition 'x == 0' is always 'false'">x == 0</warning>) System.out.println("impossible");
  }
  
  void testFallthrough(int x) {
    switch (x) {
      case 0 -> System.out.println(x);
      case 1 -> {
        System.out.println(<warning descr="Condition 'x == 0' is always 'false'">x == 0</warning>);
        System.out.println(<warning descr="Condition 'x == 1' is always 'true'">x == 1</warning>);
      }
    }
    switch (x) {
      case 0: System.out.println(x);
      case 1: {
        System.out.println(x == 0);
        System.out.println(x == 1);
      }
    }
  }
  
  void testOnlyBranch(int x) {
    if (x > 1) return;
    switch (x) {
      case 0 -> System.out.println("zero");
      case 1 -> System.out.println("one");
      case <warning descr="Switch label '2' is unreachable">2</warning> -> System.out.println("two");
    }
    if (x < 1) return;
    switch (x) {
      case 0 -> System.out.println("zero");
      case <warning descr="Switch label '1' is the only reachable in the whole switch">1</warning> -> System.out.println("one");
      case 2 -> System.out.println("two");
    }
  }
}
