// "Replace with record pattern" "true-preview"
class X {
  record Point(int x, int y) {
  }

  void test(Object obj) {
    int x = 0;
    int y = 0;
    if (obj instanceof Point(int x1, int y1)) {
      System.out.println(x1 + y1);
    }
  }
}
