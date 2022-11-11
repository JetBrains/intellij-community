// "Replace 'point' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof Point(double x, double y) && y == x) {
      Point point<caret> = (Point) obj;
    }
  }
}

record Point(double x, double y) {
}