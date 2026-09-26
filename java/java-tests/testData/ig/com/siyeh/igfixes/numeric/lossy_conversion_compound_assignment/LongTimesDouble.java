class X {
  void f() {
    long result = 80;
    double percentage = 50.0;
    result *= percentage<caret> / 100;
  }
}
