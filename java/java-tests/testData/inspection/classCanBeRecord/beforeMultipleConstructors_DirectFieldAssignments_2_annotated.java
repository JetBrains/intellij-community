// "Convert to record class" "true-preview"
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double x) {
    this.x = x;
    this.y = 0;
  }

  @Deprecated
  Point2(double x, double y) {
    this.x = x;
    this.y = y;
  }
}
