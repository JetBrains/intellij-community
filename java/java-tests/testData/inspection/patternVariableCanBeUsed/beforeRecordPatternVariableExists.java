// "Replace 'p' with existing pattern variable 'point'" "true"
class X {
  void test(Object obj) {
    if (obj instanceof Point(double x, double y) point && y == x) {
      Point p<caret> = (Point) obj;
    }
  }
}

record Point(double x, double y) {
}