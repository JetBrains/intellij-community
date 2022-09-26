// "Fix all 'Pattern variable can be used' problems in file" "true"
class Main {
  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1) point1, Point(double x2, double y2) point2) rect) {
      double a1 = rect.point1().<caret>x();
      double b1 = rect.point1().y();
      double a2 = ((((((rect)).point2())).x()));
      double b2 = rect.point2().y();
      double k1 = point1.x();
      double l1 = point1.y();
      double s = point2.x() + point2.y();
      Point p1 = rect.point1();
      System.out.println(p1);
      System.out.println(rect.point2());
      System.out.println(rect.point1().x() + a1 + b1 + a2 + b2);
      System.out.println(rect.point1().y() + k1 + l1);
      System.out.println(rect.point2().x() + s);
      System.out.println(((((((rect)).point2())).y())));
      System.out.println(point1.x());
      System.out.println(point2.x());

    }
  }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}
