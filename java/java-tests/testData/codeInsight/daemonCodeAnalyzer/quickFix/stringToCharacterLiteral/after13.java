// "Change "\\" to '\\' (to char literal)" "true"
class A {
  void m(char x) {}

  {
    m(<caret>'\\');
  }
}
