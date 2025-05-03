// "Convert to record class" "true-preview"

class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double first, double second) {
    this.y = Math.abs(first) + Math.sqrt(second);
    this.x = first;
  }
}
