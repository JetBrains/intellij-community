/*
Value is always true (x <= y; line#9)
  Condition 'x <= y' was checked before (x > y; line#7)
 */
class Test {
  void test(int x, int y) {
    if (x > y) return;

    if (<selection>x <= y</selection>) {

    }
  }
}