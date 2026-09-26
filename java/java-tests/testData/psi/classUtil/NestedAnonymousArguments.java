class NestedAnonymousArguments {
  Object first = new Object() { };
  Object nested = new A(new B(new C() { }) { }) { };
  Object body = new A(new B(null) { }) {
    Object nested = identity(new C() { });
  };

  static Object identity(Object value) {
    return value;
  }

  static class A {
    A(Object value) { }
  }

  static class B {
    B(Object value) { }
  }

  static class C { }
}
