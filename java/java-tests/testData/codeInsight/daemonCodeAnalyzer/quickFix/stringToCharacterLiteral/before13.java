// "Change "\\" to '\\' (to char literal)" "true-preview"
class A {
  void m(char x) {}

  {
    m(<caret>"\\");
  }
}
