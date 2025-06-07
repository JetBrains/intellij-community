// "Convert to local variable" "true"
class Temp {
  /**
   * @param x
   */
  void foo(int <caret>x) {
    x = 5;
  }
}