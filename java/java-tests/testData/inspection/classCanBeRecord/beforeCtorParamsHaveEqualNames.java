// "Convert to record class" "false"
class <caret>R {
  final String first;

  // red code is here
  R(String first, String first) {
    this.first = first;
  }
}