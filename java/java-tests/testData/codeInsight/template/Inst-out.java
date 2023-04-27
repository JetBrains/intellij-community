class X {

  void x(Number n) {
      if (n instanceof Number) {
          Number number = (Number) n;
          <caret>
      }
  }
}