class A {
}
class B extends A {
  void m() {
    Class<? extends A> c = g<caret>
  }
}