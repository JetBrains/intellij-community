// "Convert to record class" "true-preview"
class Foo {
  static double delta = 1;

  void test() {
      record Point(double x, double y) {

          Point shiftX() {
              return new Point(x + delta, y); // using a variable "delta" which is static
          }
      }
  }
}
