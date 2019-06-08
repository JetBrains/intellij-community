/*
Value is always false (a > 5 && b < 0 && b > a; line#15)
  One of the following happens:
    Operand #1 of &&-chain is false (a > 5; line#15)
    or operand #2 of &&-chain is false (b < 0; line#15)
    or operand #3 of &&-chain is false (b > a; line#15)
      Left operand is <= -1 (b; line#15)
        Range is known from line #15 (b < 0; line#15)
      and right operand is >= 6 (a; line#15)
        Range is known from line #15 (a > 5; line#15)
 */

class Test {
  void test(int a, int b) {
    if(<selection>a > 5 && b < 0 && b > a</selection>) {}
  }
}