record Point(double x, double y) {}
record Rect(Point point1, Point point2) {}
record A(double x, double y) {}

class Foo {
  void test1(Point[] points) {
    for (Point(double x, double y) : points) {
      System.out.println(x + y);
      System.out.println(points.length);
    }
  }

  void test2(Point[] points) {
    for (Point(double x, double y) : points) {}
  }

  void test3() {
    for (Point(double x, double y) : new Point[]{}) {}
  }

  void test4(Point[] points) {
    for (Point(double x, double y) : points) {
      System.out.println(x + y);
    }
  }

  void test5(Rect[] rectangles) {
    for (Rect(Point(double x1, double y1), Point point2) : rectangles) {}
  }
}

class Bar extends Foo {
  @Override
  void <warning descr="Method 'test1()' is identical to its super method">test1</warning>(Point[] ps) {
    for (Point(double a, double b) : ps) {
      System.out.println(a + b);
      System.out.println(ps.length);
    }
  }

  @Override
  void test2(Point[] points) {
    for (Point(<error descr="Incompatible types. Found: 'int', required: 'double'">int x</error>, <error descr="Incompatible types. Found: 'int', required: 'double'">int y</error>) : points) {}
  }

  @Override
  void test3() {
    for (A(double x, double y) : new A[]{}) {}
  }

  @Override
  void test4(Point[] points) {
    for (Point(double x, double y) : points) {
      System.out.println(x + x);
    }
  }

  @Override
  void test5(Rect[] rectangles) {
    for (Rect(Point point1, Point point2) : rectangles) {}
  }
}
