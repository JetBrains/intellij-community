class X {
  final double s = 30;
  void f() {
    long b = 1;
    b *= (long) s<caret>;
  }
}
