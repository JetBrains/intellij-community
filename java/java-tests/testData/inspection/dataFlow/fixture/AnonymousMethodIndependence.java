class A {
  void foo(final Object arg) {
    new Object() {
      void foo() {
        if (arg instanceof Number) {
          return;
        }
      }
      void foo1() {
        if (arg instanceof Number) {
          return;
        }
      }
    };
  }
}