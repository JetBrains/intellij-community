// "Convert to record class" "true-preview"
class Point2<caret> {
  private final double x;
  private final double y;
  
  Point2(double x) {
    this.x = x;
    this.y = 0;
    System.out.println("After fields are assigned");
  }

  Point2(double x, double y) {
    System.out.println("Before fields are assigned");
    this.x = x;
    this.y = y;
    System.out.println("After fields are assigned");
  }
}
