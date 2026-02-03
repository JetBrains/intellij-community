/*
Value is always true (x < y; line#13)
  Condition 'x < y' was checked before (x == y; line#9)
 */
class Test {
  void test(int x, int y) {
    if (x > y) return; // would be better to point also here, but acceptable

    if (x == y) {
      return;
    }

    if (<selection>x < y</selection>) {
      return;
    }
  }
}