// "Replace 'point' with pattern variable" "true"
class X {
  void test(Object obj) {
    if (obj instanceof Point(double x, double y) point && y == x) {
    }
  }
}

record Point(double x, double y) {
}