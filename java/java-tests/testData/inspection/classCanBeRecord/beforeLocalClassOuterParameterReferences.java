// "Convert to record class" "false"
class Foo {
  void test(double parameter) {


    class Poi<caret>nt {
      private final double x;
      private final double y;

      Point(double x, double y) {
        this.x = x;
        this.y = y;
      }

      Point shiftX() {
        return new Point(x + parameter, y); // using a parameter of the local class' enclosing method
      }
    }
  }
}
