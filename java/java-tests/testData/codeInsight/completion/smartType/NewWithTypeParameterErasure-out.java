class A<X extends Throwable> {}
class B {
  {
    m(new A<Throwable>());<caret>
  }
  void m(A<?> a) { }
}