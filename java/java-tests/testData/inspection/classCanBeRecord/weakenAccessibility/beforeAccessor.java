// "Convert to record class" "GENERIC_ERROR_OR_WARNING"
class <caret>R {
  final int first;

  R(int first) {
    this.first = first;
  }

  private int getFirst() {
    return first;
  }
}
