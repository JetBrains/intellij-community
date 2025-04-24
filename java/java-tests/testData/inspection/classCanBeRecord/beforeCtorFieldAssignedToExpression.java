// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873

class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double x, double y) {
    this.x = x;
    this.y = y + 1;
  }
}
