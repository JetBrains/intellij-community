class Main {
  record Point(int x, int y) {}

  void test(Point[] points) {
    for (Point(int x, int y) : points) {
      System.out.println(x<caret> + y);
    }
  }
}
