class SwitchExpression {

  void it() {
    long z = (long) switch(1) {
      default -> 10;
    } + 1;
  }
}