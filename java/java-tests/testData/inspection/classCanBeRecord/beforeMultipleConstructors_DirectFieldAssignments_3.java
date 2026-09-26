// "Convert to record class" "true-preview"
class Point3<caret> {
  private final double x;
  private final double y;
  private final double z;

  Point3(double x) {
    this.x = x;
    this.y = 0;
    this.z = 0;
  }

  Point3(double x, double y) {
    this.x = x;
    this.y = y;
    this.z = 0;
  }

  Point3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }
}
