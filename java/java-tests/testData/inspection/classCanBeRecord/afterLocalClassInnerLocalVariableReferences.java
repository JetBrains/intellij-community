// "Convert to record class" "true-preview"
class Foo {
  static final double staticVar = 1.0;
  
  void test() {

      record Point(double x, double y) {

          Point shiftX() {
              double myLocalVar = staticVar;
              return new Point(x, y + myLocalVar); // using a local variable but not of the local class' enclosing method
          }
      }
  }
}
