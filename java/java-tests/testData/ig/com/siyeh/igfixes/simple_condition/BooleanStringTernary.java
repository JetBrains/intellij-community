class BooleanStringTernary {
  void foo(boolean param) {
    String output = <caret>param ? "true" : "false";
  }
}