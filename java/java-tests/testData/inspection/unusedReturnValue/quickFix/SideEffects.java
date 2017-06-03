class SideEffects {

  int i;

  void m() {
    f();
  }

  int <caret>f() {
    return (i = 1) + 2;
  }
}