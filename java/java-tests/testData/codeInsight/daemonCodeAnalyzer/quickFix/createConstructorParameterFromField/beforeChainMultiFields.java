// "Add constructor parameter" "true"
class X {
  final <caret>int a;
  final int b;
  final int c;
  final int d;
  final int e;
  
  X(int b, int d, String... extra) {
    this.b = b;
    this.d = d;
  }
  
  X() {
    this(1, 2);
  }
  
  X(String s1, String s2) {
    this(3, 4, s1, s2);
  }
}