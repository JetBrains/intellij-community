// "Fix all 'Pattern variable can be used' problems in file" "true"
class Main {
  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1), Point(double x2, double y2)) rect) {
      double a1 = rect.point1().<caret>x();
      double b1 = ((((((rect)).point1())).y()));
      double b2 = rect.point2().y();
      double s = ((((((rect)).point2())).x())) + b2;
      System.out.println(rect.point1().x() + a1 + b1 + b2);
      System.out.println(rect.point1().y() + a1 + b2);
      System.out.println(((((((rect)).point2())).x())) + s);
      System.out.println(rect.point2().y());

    }
  }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}
