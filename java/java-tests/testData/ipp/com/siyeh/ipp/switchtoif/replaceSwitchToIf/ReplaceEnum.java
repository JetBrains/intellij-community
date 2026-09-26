class Test {
  void test() {
    enum P {
      a, b, c;
    }
    P p = null;
    <caret>switch (p) {
      case a, b -> {
      }
      case c -> {
      }
      default -> {
      }
    }
  }
}