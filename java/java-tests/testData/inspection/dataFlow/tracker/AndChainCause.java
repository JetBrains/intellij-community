/*
Value is always false (x > 6 && y > 10)
  Operand #1 of &&-chain is false (x > 6)
    Left operand is 5 (x)
      'x' was assigned (=)
 */

class Test {
  void test(int y) {
    int x = 5;
    if(<selection>x > 6 && y > 10</selection>) {}
  }
}