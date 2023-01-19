class Main {
  record Point(int x, int y) {
  }

  record Rect(Point point1, Point point2) {
  }
  void test5(final Point[] points) {
    for (Point(int <warning descr="Variable 'x' can have 'final' modifier">x</warning>, int y) : points) {
      System.out.println(x);
      y = 42;
    }
  }
}
