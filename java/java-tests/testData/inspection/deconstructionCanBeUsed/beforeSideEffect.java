// "Replace with record pattern" "false"
class X {
  record Point(int x, int y) {
    public static int sideEffect = 0;

    @Override
    public int x() {
      sideEffect++;
      return x;
    }
  }

  void test(Object obj) {
    if (obj instanceof Po<caret>int point) {
      int x = point.x();
      int y = point.y();
      System.out.println(x + y);
    }
  }
}
