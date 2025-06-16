// "Convert to record class" "true-preview"
class Point3<caret> {
  private final double x;
  private final double y;
  private final double z;

  Point3(double x) {
    this(x, 0);
  }

  Point3(double x, double y) {
    this(x, y, 0);
  }

  Point3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
}
