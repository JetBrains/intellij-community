class T {
  static class P { String n; }

  static boolean notSame(P a, P b) {
    return a.n == null || !a.n.<caret>equals(b.n);
  }
}