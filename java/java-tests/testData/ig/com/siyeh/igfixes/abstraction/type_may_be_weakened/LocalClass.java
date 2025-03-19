class LocalClass {

  void foo() {
    class A<T> {
      void foo() {}
    }
    class B<T> extends A<T> {}
    B<String> b<caret>b = new B<>();
    bb.foo();
  }
}