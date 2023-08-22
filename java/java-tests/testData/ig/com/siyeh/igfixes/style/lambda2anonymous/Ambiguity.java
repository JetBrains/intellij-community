interface I1 {
  void m();
}

interface I2 {
  void m();
}

class Ambiguity1 {

  static void m(I1 i1) {}
  static void m(I2 i2) {}

  {
    m((<caret>)->{throw new AssertionError();});
  }
}
