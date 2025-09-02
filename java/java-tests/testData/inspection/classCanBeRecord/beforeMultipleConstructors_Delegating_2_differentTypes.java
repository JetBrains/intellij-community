// "Convert to record class" "true-preview"
class Point2<caret> {
  private final double x;
  private final double y;

  // Classify: non-canonical, redirect to non-canonical: this(String, String)
  Point2(String x) {
    this(x, "0");
    System.out.println("Point2(String)");
  }

  // Classify: non-canonical, redirect to canonical: this(double, double)
  Point2(String x, String y) {
    this(Double.parseDouble(x), Double.parseDouble(y));
    System.out.println("Point2(String, String)");
  }

  // Classify: non-canonical, redirect to canonical: this(double, double)
  Point2(double x) {
    this(x, 0);
    System.out.println("Point2(double)");
  }

  // Classify: canonical, no redirect
  Point2(double x, double y) {
    System.out.println("Point2(double, double)");
    this.x = x;
    this.y = y;
  }
}
