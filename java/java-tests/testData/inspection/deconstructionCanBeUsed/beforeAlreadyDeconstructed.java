// "Replace with record pattern" "false"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    if (obj instanceof Po<caret>int(int x, int y) point) {
      int k = point.x();
      int m = point.y();
      System.out.println(k + l);
    }
  }
}
