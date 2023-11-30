class SwitchExpression {

  void it() {
    long z = (long) (<caret>switch(1) {
      default -> 10;
    }) + 1;
  }
}