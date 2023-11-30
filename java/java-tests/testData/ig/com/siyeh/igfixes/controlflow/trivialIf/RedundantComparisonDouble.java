class Test {
  String test(double a) {
    <caret>if(a == 0.0) return 0.0;
    return a;
  }
}