/*
Value is always false (x < 0; line#12)
  Left operand is in {0..65535} (x; line#12)
    'x' was assigned (=; line#11)
 */

import java.util.List;

class Test {
  void test(char c) {
    int x = c;
    if (<selection>x < 0</selection>) {}
  }
}