/*
Value is always false (((char) x) < 0; line#10)
  Result of '(char)' cast is in {0..65535} ((char) x; line#10)
 */

import java.util.List;

class Test {
  void test(int x) {
    if (<selection>((char) x) < 0</selection>) {}
  }
}