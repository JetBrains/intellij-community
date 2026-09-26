// "Remove pattern variable" "false"
class Test {
  record Point(double x, double y) {}

  record Rect(Point point1, Point point2) {}

  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x<caret>, double y) point1, Point point2) rect) {}
  }
}