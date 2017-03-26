class SideEffects {

  int i;

  void m() {
    f();
  }

  void f() {
      i = 1;
  }
}