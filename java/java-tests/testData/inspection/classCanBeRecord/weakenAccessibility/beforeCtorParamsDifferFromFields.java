// "Convert to record class" "true-preview"

// Test for IDEA-371419 and IDEA-371645
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double first, double second) {
    this.x = first;
    this.y = second;
    System.out.println("created");
  }
}
