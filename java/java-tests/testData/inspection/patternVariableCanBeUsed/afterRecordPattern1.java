// "Fix all 'Pattern variable can be used' problems in file" "true"
class Main {
  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x1, double y1), Point(double x2, double y2))) {
        double s = x2 + y2;
      Point p1 = ((Rect) obj).point1();
      System.out.println(p1);
      System.out.println(((Rect) obj).point2());
      System.out.println(x1 + x1 + y1 + x2 + y2);
      System.out.println(y1 + x1 + y1);
      System.out.println(x2 + s);
      System.out.println(((y2)));
      System.out.println(x1);
      System.out.println(x2);

    }
  }
}

record Point(double x, double y) {
}

record Rect(Point point1, Point point2) {
}
