class Main {
  record Point(int x, int y) {}

  Point[] getPoints(int x) {
    return new Point[0];
  }

  void test1(Point[] points) {
    for (Point(int x, int y) : points) {
      System.out.println(x + y);
    }
  }

  void test2() {
    System.out.println(<error descr="Cannot resolve symbol 'x'">x</error>);
    for (Point(int x, int y) : getPoints(<error descr="Cannot resolve symbol 'x'">x</error>)) {
    }
    System.out.println(<error descr="Cannot resolve symbol 'y'">y</error>);
  }
}
