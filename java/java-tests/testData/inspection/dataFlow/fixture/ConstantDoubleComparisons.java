class Foo {

  private void foo() {
    double d = Double.MIN_VALUE;
    while (true) {
      if (d == Double.MIN_VALUE) {
        d = 0;
      }
    }
  }
}