// "Convert to record class" "false"
class <caret>R {
  final int first;

  R(int first) {
    this.first = first;
  }

  private int getFirst() {
    return first;
  }
}