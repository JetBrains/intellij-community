// "Replace with record pattern" "true-preview"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    int x = 0;
    int y = 0;
    if (obj instanceof Poi<caret>nt point) {
      System.out.println(point.x() + point.y());
    }
  }
}
