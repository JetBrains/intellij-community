class ConstructorArgumentClassNames {
  static class A {
    A(Object value) { }
  }

  static class B { }

  Object value = new A(new B() { }) { };
}
