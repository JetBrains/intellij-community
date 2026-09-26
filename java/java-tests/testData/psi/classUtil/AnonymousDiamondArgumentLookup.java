class AnonymousDiamondArgumentLookup {
  Object value = new Outer(new Generic<>() { }) { };

  static class Outer {
    Outer(Object value) { }
  }

  static class Generic<T> { }
}
