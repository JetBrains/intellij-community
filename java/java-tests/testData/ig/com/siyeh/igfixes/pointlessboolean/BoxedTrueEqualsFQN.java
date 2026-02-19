class Boxed {
  String method(boolean value) {
    if (java.lang.Boolean.TRUE.equa<caret>ls(value)) {
      return "foo";
    }
    return "baz";
  }
}
