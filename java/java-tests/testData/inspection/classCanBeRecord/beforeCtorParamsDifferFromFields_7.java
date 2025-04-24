// "Convert to record class" "true"
// no "true-preview" above because of IDEA-369873

// Test for IDEA-371419
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double first, double second) {
    this.x = first;
    this.y = second + 1;
  }
}
