// "Remove pattern variable" "true-preview"
class Test {
  record Point(double x, double y) {}

  record Rect(Point point1, Point point2) {}

  void foo(Object obj) {
    if (obj instanceof Rect(Point(double x, double y) point1<caret>, Point point2) rect) {}
  }
}