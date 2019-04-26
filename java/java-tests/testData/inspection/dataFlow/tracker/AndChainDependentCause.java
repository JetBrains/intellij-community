/*
Value is always false (a > 5 && b < 0 && b > a)
  One of the following happens:
    Operand #1 of &&-chain is false (a > 5)
    Operand #2 of &&-chain is false (b < 0)
    Operand #3 of &&-chain is false (b > a)
      Left operand is <= -1 (b)
        Range is known from line #15 (b < 0)
      Right operand is >= 6 (a)
        Range is known from line #15 (a > 5)
 */

class Test {
  void test(int a, int b) {
    if(<selection>a > 5 && b < 0 && b > a</selection>) {}
  }
}