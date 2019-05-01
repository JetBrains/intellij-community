/*
Value is always true (x <= y)
  Condition 'x <= y' was checked before (x > y)
 */
class Test {
  void test(int x, int y) {
    if (x > y) return;

    if (<selection>x <= y</selection>) {

    }
  }
}