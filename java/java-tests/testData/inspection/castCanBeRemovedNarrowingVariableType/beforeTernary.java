// "Change type of 'p' to 'Point' and remove cast" "true"
class Test {
  class Point {int x, y;}

  int test(boolean b) {
    Object p = new Point();
    return b ? 0 : ((Po<caret>int)p).x;
  }
}