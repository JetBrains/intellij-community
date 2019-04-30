/*
Value is always true (x || (a+b)+(c+d)==10)
  One of the following happens:
    Operand #1 of ||-chain is true (x)
    or operand #2 of ||-chain is true ((a+b)+(c+d)==10)
      Result of '+' is 10 ((a+b)+(c+d))
        Result of '+' is 3 (a+b)
          Left operand is 1 (a)
            'a' was assigned (=)
          and right operand is 2 (b)
            'b' was assigned (=)
        and result of '+' is 7 (c+d)
          Left operand is 3 (c)
            'c' was assigned (=)
          and right operand is 4 (d)
            'd' was assigned (=)
 */

class Test {
  void test(boolean x) {
    int a = 1, b = 2, c = 3, d = 4;
    if(<selection>x || (a+b)+(c+d)==10</selection>) {}
  }
}