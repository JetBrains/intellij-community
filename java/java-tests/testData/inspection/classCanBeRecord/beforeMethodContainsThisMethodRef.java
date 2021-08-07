// "Convert to a record" "true"
class <caret>R {
  final int first;

  R(int first) {
    this.first = first;
  }

  void print() {
    System.out.println(toString());
  }
}