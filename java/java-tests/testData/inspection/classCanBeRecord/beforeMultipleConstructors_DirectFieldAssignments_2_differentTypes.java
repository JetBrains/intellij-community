// "Convert to record class" "true-preview"
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double x) {
    this.x = x;
    this.y = 0;
  }

  Point2(String x) {
    this.x = Double.parseDouble(x);
    this.y = 0;
  }

  Point2(String x, String y) {
    this.x = Double.parseDouble(x);
    this.y = Double.parseDouble(y);
  }

  /// Classify: canonical, no redirect
  Point2(double x, double y) {
    this.x = x;
    this.y = y;
  }
}
