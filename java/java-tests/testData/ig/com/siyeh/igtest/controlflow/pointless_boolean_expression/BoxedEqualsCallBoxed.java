class Boxed {
  String method(boolean value) {
    // returnsBool can be overridden in a subclass with a different implementation. We can't know if it can return null.
    if (Boolean.TRUE.equals(returnsBool(value))) {
      return "foo";
    }
    return "baz";
  }

  public Boolean returnsBool(boolean value) {
    return Math.random() > 0.5;
  }
}
