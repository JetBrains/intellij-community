class Parentheses {
  void test() {
    X[] constants = {(<caret>X.A), (X.B), (X.C)};
  }

  enum X {
    A, B, C
  }
}