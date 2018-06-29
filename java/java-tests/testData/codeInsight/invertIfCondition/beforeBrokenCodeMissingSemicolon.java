// "Invert 'if' condition" "false"

class P {
  Object x;
  static final String Y = "y";

  public P foo(P a, P b) {
    if (x <caret>instanceof String) Baz
    a.bar(b, P.Y);
    return a;
  }
  void bar(P p, String s) { }
}