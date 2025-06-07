// "Convert to record class" "true-preview"
class Point2<caret> {
  final double x;
  final double y;

  // This is an additional constructor.
  Point2(double x) {
    // Before
    this.x = x; // After assign X
    this.y /* crazy */ = 0; // After assign Y
    System.out.println("ctor 1: after fields are assigned");
    // After
  }

  /// This is a canonical constructor.
  Point2(double x, double y) {
    // Before
    System.out.println("ctor 2: before fields are assigned");
    this.x = x;
    // In the middle
    this.y = y;
    // After
  }
}
