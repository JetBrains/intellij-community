class X {
  void vm(Double a, Double b) {
    boolean r = !(<caret>a != null) || b != null;
  }
}