// "Convert to record class" "false"
// Reason: not implemented

class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double first, double second) {
    this.y = Math.abs(second) + Math.sqrt(first);
    this.x = first;
  }
}
