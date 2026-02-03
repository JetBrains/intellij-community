class Boxed {
  String method(boolean value) {
    if (Boolean.TRUE.equa<caret>ls(/*bruh*/ value)) {
      return "foo";
    }
    return "baz";
  }
}
