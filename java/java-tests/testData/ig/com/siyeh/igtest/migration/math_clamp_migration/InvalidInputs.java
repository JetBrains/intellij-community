class InvalidInputs {
  void ICannotCode(int input) {
    input = Math.min(<error descr="Expression expected">,</error>Math.max(5, input));
  }
}