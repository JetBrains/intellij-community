class BooleanStringTernaryDuplicateClass {
  void foo(boolean param) {
    String output = <caret>param ? "true" : "false";
  }
}

class Boolean {
}