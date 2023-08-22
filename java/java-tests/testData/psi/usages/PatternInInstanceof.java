record Rec(int x, String s) {
  void foo(Object o) {
    if (o instanceof <caret>Rec(int patternX, String s) r) {
    }
  }
}