class LongValue {

  void x(long <warning descr="Value of parameter 'l' is always '10000000000L'"><caret>l</warning>) {
    System.out.println(l);
  }

  void y() {
    x(10000000000L);
  }
}