/*
Value is always true (x || (a+b)+(c+d)==10; line#22)
  One of the following happens:
    Operand #1 of ||-chain is true (x; line#22)
    or operand #2 of ||-chain is true ((a+b)+(c+d)==10; line#22)
      Result of '+' is 10 ((a+b)+(c+d); line#22)
        Result of '+' is 3 (a+b; line#22)
          Left operand is 1 (a; line#22)
            'a' was assigned to '1' (=; line#21)
          and right operand is 2 (b; line#22)
            'b' was assigned to '2' (=; line#21)
        and result of '+' is 7 (c+d; line#22)
          Left operand is 3 (c; line#22)
            'c' was assigned to '3' (=; line#21)
          and right operand is 4 (d; line#22)
            'd' was assigned to '4' (=; line#21)
 */

class Test {
  void test(boolean x) {
    int a = 1, b = 2, c = 3, d = 4;
    if(<selection>x || (a+b)+(c+d)==10</selection>) {}
  }
}