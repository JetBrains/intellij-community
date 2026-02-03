record Rec(int x, String s) {
  void foo(Object o) {
    switch (o) {
      case <caret>Rec(int patternX, String s) -> {}
    }
  }
}