record Complex(double x, double y) {
  double methodReturnsX() {
    return x;
  }

  double getModulus() {
    return Math.hypot(x, y);
  }
}

class Use {
  void test(Complex c) {
    System.out.println(c.get<caret>Modulus());
  }
}