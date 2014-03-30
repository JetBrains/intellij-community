class A<X extends Throwable> {}
class B {
  {
    m(new <caret>)
  }
  void m(A<?> a) { }
}