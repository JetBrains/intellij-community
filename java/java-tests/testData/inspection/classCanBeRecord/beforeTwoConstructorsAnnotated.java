// "Convert to record class" "true-preview"

class Poi<caret>nt {
    final double x;
    final double y;

    Point(double x) {
      this.x = x;
      this.y = 0;
    }

    @Deprecated
    Point(double x, double y) {
      this.x = x;
      this.y = y;
    }
}
