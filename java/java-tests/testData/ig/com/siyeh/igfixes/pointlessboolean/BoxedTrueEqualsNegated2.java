class Boxed {
  String method(boolean value) {
    if (Boolean.TRUE.equa<caret>ls(!value)) {
      return "foo";
    }
    return "baz";
  }
}
