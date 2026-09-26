class AnonymousArgumentScopes {
  Object value = new A((Runnable)() -> {
    class Local {
      Object member = new Object() { };
    }
  }) { };
  Object next = identity(new Object() { });

  static Object identity(Object value) {
    return value;
  }

  static class A {
    A(Runnable value) { }
  }
}
