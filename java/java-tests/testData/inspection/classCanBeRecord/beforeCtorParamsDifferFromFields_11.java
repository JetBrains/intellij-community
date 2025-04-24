// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873

class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double first, double second) {
    this.y = Math.abs(first) + Math.sqrt(second);
    this.x = first;
  }
}
