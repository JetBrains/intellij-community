class Boxed {
  String method(boolean value) {
    // test double negation
    if (value) {
      return "foo";
    }
    return "baz";
  }
}
