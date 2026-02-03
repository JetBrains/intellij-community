// "Convert to record class" "false"
class <caret>R {
  final String first;

  // red code is here: duplicated parameter names
  R(String first, String first) {
    this.first = first;
  }
}