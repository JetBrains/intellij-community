class X {
  
  void foo() {
    new Object() {
      static void <caret>x(X x) {}
    }
  }
}