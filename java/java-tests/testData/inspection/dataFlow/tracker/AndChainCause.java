/*
Value is always false (x > 6 && y > 10; line#11)
  Operand #1 of and-chain is false (x > 6; line#11)
    Left operand is 5 (x; line#11)
      'x' was assigned to '5' (=; line#10)
 */

class Test {
  void test(int y) {
    int x = 5;
    if(<selection>x > 6 && y > 10</selection>) {}
  }
}