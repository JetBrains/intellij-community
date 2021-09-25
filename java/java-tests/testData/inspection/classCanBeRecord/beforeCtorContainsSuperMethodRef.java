// "Convert to a record" "false"
class <caret>R {
  final int first;
  final String second;

  R(int first, String second) {
    this.first = first;
    this.second = super.toString() + second;
  }
}