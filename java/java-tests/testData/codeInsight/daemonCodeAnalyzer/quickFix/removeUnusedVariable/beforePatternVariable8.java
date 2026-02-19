// "Remove pattern variable" "false"
class Test {
  record Point(double x, double y) {}

  record Rect(Point point1, Point point2) {}

  void foo(Object obj) {
    switch (obj) {
      case Rect(Point(double x, double y<caret>) point1, Point point2) rect -> {}
      default -> {}
    }
  }
}