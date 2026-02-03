class ErrorElement {

  void m() {
    boolean b = 1 == 2 ||<caret> 3 == .x;
  }
}