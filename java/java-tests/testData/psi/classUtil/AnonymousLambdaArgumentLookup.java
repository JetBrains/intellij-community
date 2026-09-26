class AnonymousLambdaArgumentLookup {
  Object value = new Outer(() -> new Object() { }) { };

  static class Outer {
    Outer(Runnable value) { }
  }
}
