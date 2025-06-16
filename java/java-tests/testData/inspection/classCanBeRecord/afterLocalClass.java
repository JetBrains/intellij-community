// "Convert to record class" "true-preview"
class Foo {
  void test() {
      record Point(double x, double y) {

          Point shiftX() {
              return new Point(x + 1, y);
          }

          double nestedWithParameter(int myParameter) {
              return 42 + myParameter + x;
          }
      }
  }
}
