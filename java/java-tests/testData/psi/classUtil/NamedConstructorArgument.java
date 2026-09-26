class NamedConstructorArgument {
  Object value = new A(new B()) { };
  Object next = identity(new Object() { });

  static Object identity(Object value) {
    return value;
  }

  static class A {
    A(Object value) { }
  }

  static class B { }
}
