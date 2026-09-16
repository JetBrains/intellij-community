class NestedConstructorArguments {
  Object value = new A(new A(new Object() { }) { }) {
    Object member = new Object() { };
  };

  static class A {
    A(Object value) { }
  }
}
