class X {

  void m(X x2) {
    if (equa<caret>ls(x2, x2, x2)) {

    }
  }

  boolean equals(X x, X x2, X x3) {
    return false;
  }
}