// "Fix all 'Pattern variable can be used' problems in file" "true"
class Main {
  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1), Point(double x2, double y2))) {
      double a1 = ((Rect) obj).point1().x(<caret>);
      double b1 = ((Rect) obj).point1().y();
      double a2 = ((((((((Rect) obj))).point2())).x()));
      double b2 = ((Rect) obj).point2().y();
      double k1 = ((Rect) obj).point1().x();
      double l1 = ((Rect) obj).point1().y();
      double s = ((Rect) obj).point2().x() + ((Rect) obj).point2().y();
      Point p1 = ((Rect) obj).point1();
      System.out.println(p1);
      System.out.println(((Rect) obj).point2());
      System.out.println(((Rect) obj).point1().x() + a1 + b1 + a2 + b2);
      System.out.println(((Rect) obj).point1().y() + k1 + l1);
      System.out.println(((Rect) obj).point2().x() + s);
      System.out.println(((((((((Rect) obj))).point2())).y())));
      System.out.println(((Rect) obj).point1().x());
      System.out.println(((Rect) obj).point2().x());

    }
  }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}
