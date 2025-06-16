// "Convert to record class" "false"
class Foo {
  void test() {
    class Poi<caret>nt {
      private final double x;
      private final double y;

      Point(double x, double y) {
        this.x = x;
        this.y = y;
      }

      double getX() {
        return x;
      }

      double getY() {
        return y;
      }

      Point shiftX() {
        return new Point(x + 1, y + doesNotExist);
      }
    }
  }
}
