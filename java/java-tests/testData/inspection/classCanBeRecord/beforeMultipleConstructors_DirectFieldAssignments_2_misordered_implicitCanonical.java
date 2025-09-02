// "Convert to record class" "false"

// Converting this to record would create a new constructor (the record canonical constructor), which
// may not be desirable.
class Point2<caret> {
  private final double x;
  private final double y;

  Point2(double actuallyY) {
    this.y = actuallyY;
    this.x = 0;
  }
}
