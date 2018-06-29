// "Fold expression into Stream chain" "true"
class Test {
  String foo(int a, int b, int c, int d) {
    return a * 2 + "|" + b * 2 + "|" + c * 2 + "|"<caret> + d * 2;
  }
}