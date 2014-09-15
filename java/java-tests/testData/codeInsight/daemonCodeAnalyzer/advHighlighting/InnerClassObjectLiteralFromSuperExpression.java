class C {
  C(Class c) {}
}

class A extends C {
  A() {
    super(B.class);
  }

  class B {}
}