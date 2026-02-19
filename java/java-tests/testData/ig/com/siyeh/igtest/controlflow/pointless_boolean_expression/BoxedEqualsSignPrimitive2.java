class Boxed {
  void f(Boolean b) {
    // We don't know if b can be nullable or not
    if (<caret>b == (false)) {}
  }
}
