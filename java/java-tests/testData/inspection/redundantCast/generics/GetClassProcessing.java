class B { }
class A extends B { }

class C {
  void m(Class<? extends A> c) {}

  void x(B b) {
    m(((A)b).getClass());
  }
}
