class Main {
  record Point(int x, int y) {}

  void test(Point[] points) {
    for (Point(int <caret>x, int y) : points) {
      System.out.println(x + y);
    }
  }
}
