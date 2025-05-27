// "Convert to record class" "false"
// Reason: not implemented

class Main<caret> {
  final int a;
  final int b;

  Main(int first, int second) {
    this.a = first + second;
    this.b = second + first;
  }
}
