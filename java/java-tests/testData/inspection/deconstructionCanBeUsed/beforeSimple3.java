// "Replace with record pattern" "false"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    if (obj instanceof Po<caret>int p) {
      int l = p.x();
      p = new Point(4, 2);
      int m = p.y();
      System.out.println(l + m);
    }
  }
}
