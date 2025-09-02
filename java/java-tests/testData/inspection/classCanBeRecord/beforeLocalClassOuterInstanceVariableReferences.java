// "Convert to record class" "false"
class Foo {
  double delta = 1;
  
  void test() {
    class Poi<caret>nt {
      private final double x;
      private final double y;

      Point(double x, double y) {
        this.x = x;
        this.y = y;
      }

      Point shiftX() {
        return new Point(x + delta, y); // using an instance variable "delta" of the topmost class
      }
    }
  }
}
