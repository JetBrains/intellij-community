class C {
  boolean f() {
    if (f() && new Object(){<caret>})
  }
}