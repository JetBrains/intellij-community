// "Convert to record class" "true-preview"
class Foo {
  static double delta = 1;

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
        return new Point(x + delta, y); // using a variable "delta" which is static
      }
    }
  }
}
