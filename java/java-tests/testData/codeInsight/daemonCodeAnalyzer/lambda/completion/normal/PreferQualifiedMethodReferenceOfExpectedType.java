class C {
  void tryToSum() {
    mapToDouble(<caret>)
  }

  void mapToDouble(java.util.function.ToDoubleFunction<Double> mapper) {}
}