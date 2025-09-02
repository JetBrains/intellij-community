// "Convert to record class" "false"
// Reason: not implemented

class Main<caret> {
  final int a;
  final int b;

  Main(int a, int b) {
    this.a = b + a;
    this.b = b;
  }
}
