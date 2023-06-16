class Vararg {

  void x(byte... <warning descr="Actual value of parameter 'bs' is always '[(byte)1]'"><caret>bs</warning>) {
    for (byte b : bs) {

    }
  }

  void y() {
    x((byte)1);
  }
}