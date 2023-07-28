class Test {
  record R(int x, int y) {}
  void test(Object obj) {
    if (obj instanceof R(_, var b)) {
      return;
    }
    if (<warning descr="Condition 'obj instanceof R(var a, var b)' is always 'false'">obj instanceof R(var a, var b)</warning>) {
      return;
    }
    if (<warning descr="Condition 'obj instanceof R(int a, _)' is always 'false'">obj instanceof R(int a, _)</warning>) {
      return;
    }
  }

  void testExhaustiveness(I a) {
    boolean r3 = switch (a) {
      case R1(_) -> true;
      case R2(_) -> false;
    };
  }

  sealed interface I {};
  record R1(int n1) implements I {};
  record R2(int n1) implements I {};
}