// "Convert to record class" "true-preview"
class <caret>R {
  // third field
  static int third = 1;
  // fourth field
  private static final String fourth = "fourth";

  final int first, second;

  R(int first, int second) {
    this.first = first;
    this.second = second;
  }
}
