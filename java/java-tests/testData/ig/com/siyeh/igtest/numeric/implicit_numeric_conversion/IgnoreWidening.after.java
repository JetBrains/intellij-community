class IgnoreWidening {
  void m(int i) {
    long l = i;
    byte b = 2;
  }

  void f() {
    double d = 3.14;
    int i = 42;
    i += d;
    d += 2;
    d += i;
    byte a = 1;
    byte b = 2;
      a = (byte) (a + b);
  }
}