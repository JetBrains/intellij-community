/*
Value is always true (x < y; line#13)
  Condition 'x < y' was checked before (x > y; line#7)
 */
class Test {
  void test(int x, int y) {
    if (x > y) return;

    if (x == y) { // explanation doesn't point here: not entirely correct, but hard to fix; postponed
      return;
    }

    if (<selection>x < y</selection>) {
      return;
    }
  }
}