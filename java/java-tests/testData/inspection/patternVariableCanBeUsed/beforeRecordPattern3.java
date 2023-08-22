// "Fix all 'Pattern variable can be used' problems in file" "true"
class Main {
  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1) point1, Point(final double x2, double y2)) rect) {
      System.out.println(rect.point2().x(<caret>));
      double x = x1;
      Point p1 = rect.point2();
      System.out.println(rect.point2().z());
      System.out.println(rect.point1().y());
      System.out.println(rect.point1().z());
      y2 = 0.0;
      System.out.println(rect.point2().y());
      point1 = new Point(100.0, 200.0);
      System.out.println(point1.y());
      double d = rect.point2().x();
      d = 10.0;
    }
  }
}

record Point(double x, double y) {
  double z() {
    return 1.0;
  }
}

record Rect(Point point1, Point point2) {
  public Point point1() {
    return new Point(1.0, 0.0);
  }
}