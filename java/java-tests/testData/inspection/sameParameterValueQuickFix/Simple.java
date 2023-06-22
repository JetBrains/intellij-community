class Simple {

  void x(int <warning descr="Actual value of parameter 'i' is always '10'"><caret>i</warning>) {
    System.out.println(i);
  }

  void y() {
    x(10);
  }
}