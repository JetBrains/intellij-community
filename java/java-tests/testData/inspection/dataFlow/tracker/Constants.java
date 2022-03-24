/*
Value is always false (E.A == E.B; line#11)
  Values cannot be equal because E.A.ordinal != E.B.ordinal
    Left operand is 0 (E.A; line#11)
    and right operand is 1 (E.B; line#11)
 */
class Test {
  enum E {A, B, C}

  void test() {
    if (<selection>E.A == E.B</selection>) {

    }
  }
}