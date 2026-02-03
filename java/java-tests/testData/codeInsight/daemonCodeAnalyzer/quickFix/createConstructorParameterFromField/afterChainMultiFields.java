// "Add constructor parameter" "true"
class X {
  final int a;
  final int b;
  final int c;
  final int d;
  final int e;
  
  X(int a, int b, int c, int d, int e, String... extra) {
      this.a = a;
      this.b = b;
      this.c = c;
      this.d = d;
      this.e = e;
  }
  
  X(int a, int c, int e) {
    this(a, 1, c, 2, e);
  }
  
  X(String s1, String s2, int a, int c, int e) {
    this(a, 3, c, 4, e, s1, s2);
  }
}