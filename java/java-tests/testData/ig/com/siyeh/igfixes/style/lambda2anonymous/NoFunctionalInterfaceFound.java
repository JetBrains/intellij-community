class X {
  void bar(String s) {}
  {
    bar((<caret>)->{});
  }
}