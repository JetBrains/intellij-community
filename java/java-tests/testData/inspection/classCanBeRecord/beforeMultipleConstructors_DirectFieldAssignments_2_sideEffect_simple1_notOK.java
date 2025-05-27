// "Convert to record class" "false"
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double x) {
    // Cannot be converted because: Call to 'this()' must be first statement in constructor body
    System.out.println("ctor 1: before fields are assigned");
    this.x = x;
    this.y = 0;
  }

  Point2(double x, double y) {
    System.out.println("ctor 2: before fields are assigned");
    this.x = x;
    this.y = y;
  }
}
