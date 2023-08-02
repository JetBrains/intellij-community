public class UnnamedPatterns {
  record R(int a, int b) {}
  
  void test(Object obj) {
    if (obj instanceof <error descr="Using '_' as a reference is not allowed">_</error>) {}
    
    if (obj instanceof R(_, _)) {}
    if (obj instanceof R(int a, _)) {
      System.out.println(a);
    }
    if (obj instanceof R(_, int b)) {
      System.out.println(b);
    }
    if (obj instanceof R(_, _, <error descr="Incorrect number of nested patterns: expected 2 but found 3">_)</error>) {
    }
  }

  void testSwitch(Object obj) {
    switch (obj) {
      case R(_, var b) -> {
      }
      case <error descr="Label is dominated by a preceding case label 'R(_, var b)'">R(var c, var b)</error> -> {
      }
      case <error descr="Label is dominated by a preceding case label 'R(_, var b)'">R(int a, _)</error> -> {
      }
      default -> {
      }
    }
  }

  void testExhaustiveness(I a) {
    boolean r3 = switch (a) {
      case R1(_) -> true;
      case R2(_) -> false;
    };
  }

  void testNonExhaustiveness(I a) {
    boolean r3 = switch (<error descr="'switch' expression does not cover all possible input values">a</error>) {
      case R1(_) -> true;
    };
  }

  sealed interface I {};
  record R1(int n1) implements I {};
  record R2(int n1) implements I {};
}