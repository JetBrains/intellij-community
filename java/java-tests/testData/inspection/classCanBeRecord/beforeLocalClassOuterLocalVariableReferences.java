// "Convert to record class" "false"
class Foo {
  void test() {
    double delta = 1;

    class Poi<caret>nt {
      private final double x;
      private final double y;

      Point(double x, double y) {
        this.x = x;
        this.y = y;
      }

      Point shiftX() {
        return new Point(x + delta, y); // using a local variable "delta" of the local class' enclosing method
      }
    }
  }
}
