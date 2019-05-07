/*
Value is always false (E.A == E.B; line#9)
  Comparison arguments are different constants (==; line#9)
 */
class Test {
  enum E {A, B, C}

  void test() {
    if (<selection>E.A == E.B</selection>) {

    }
  }
}