class Boxed {
  String method(boolean value) {
    if (Boolean.FALSE.equa<caret>ls(value)) {
      return "foo";
    }
    return "baz";
  }
}
