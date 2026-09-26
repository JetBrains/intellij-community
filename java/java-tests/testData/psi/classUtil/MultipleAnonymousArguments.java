class MultipleAnonymousArguments {
  Object value = new A(new B() { }, new C() { }) { };

  static class A {
    A(Object first, Object second) { }
  }

  static class B { }

  static class C { }
}
