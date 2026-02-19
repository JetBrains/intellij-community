public class FieldAliasing {
  static class X {
    int a = 1;
  }

  static void moreSpecificValue(int val, X a, X b) {
    if (val < 0 || val > 10) return;
    a.a = val;
    if (<warning descr="Condition 'a.a == val' is always 'true'">a.a == val</warning>) {
      // "quite expected"
    }
    b.a = 3;
    if (<warning descr="Condition 'a.a >= 0 && a.a <= 10' is always 'true'"><warning descr="Condition 'a.a >= 0' is always 'true'">a.a >= 0</warning> && <warning descr="Condition 'a.a <= 10' is always 'true' when reached">a.a <= 10</warning></warning>) {
      // "always"
    }
    if (a.a == val) {
      // "not aliased"
    }
  }

  static void test(X a, X b) {
    a.a = 2;
    b.a = 3;
    if (a.a == 3) {
      // "aliased"
    }
    if (<warning descr="Condition 'a.a == 3 || a.a == 2' is always 'true'">a.a == 3 || <warning descr="Condition 'a.a == 2' is always 'true' when reached">a.a == 2</warning></warning>) {
      // "no other value possible"
    }
  }

  static void noAliasingPossible(X a, X b) {
    if (a == b) return;
    a.a = 2;
    b.a = 3;
    if (<warning descr="Condition 'a.a == 3' is always 'false'">a.a == 3</warning>) {
      // "aliased"
    }
  }

  public static void main(String[] args) {
    X a = new X();
    test(a, a);
  }
}