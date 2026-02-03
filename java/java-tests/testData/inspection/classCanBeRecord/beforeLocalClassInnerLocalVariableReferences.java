// "Convert to record class" "true-preview"
class Foo {
  static final double staticVar = 1.0;
  
  void test() {

    class Poi<caret>nt {
      private final double x;
      private final double y;

      Point(double x, double y) {
        this.x = x;
        this.y = y;
      }

      Point shiftX() {
        double myLocalVar = staticVar;
        return new Point(x, y + myLocalVar); // using a local variable but not of the local class' enclosing method
      }
    }
  }
}
