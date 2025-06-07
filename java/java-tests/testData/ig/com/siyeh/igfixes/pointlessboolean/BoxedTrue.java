class Boxed {
  String method(boolean value) {
    if (value == Boolean.T<caret>RUE) {
      return "foo";
    }
    return "baz";
  }
}
