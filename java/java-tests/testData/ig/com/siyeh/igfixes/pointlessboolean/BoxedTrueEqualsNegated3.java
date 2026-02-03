class Boxed {
  String method(boolean value) {
    // test double negation
    if (Boolean.FALSE.equa<caret>ls(!value)) {
      return "foo";
    }
    return "baz";
  }
}
