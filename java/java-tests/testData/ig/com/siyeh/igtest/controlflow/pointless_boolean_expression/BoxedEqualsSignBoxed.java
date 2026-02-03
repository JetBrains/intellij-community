class Boxed {
  String method() {
    // Note 1: we are comparing references, and it's still quite possible to create new instances of Boolean.
    // Note 2: returnsBool can be overridden in a subclass with a different implementation. We can't know if it can return null.
    if (returnsBool() == Boolean.TRUE) {
      return "foo";
    }
    return "baz";
  }

  public Boolean returnsBool() {
    return Math.random() > 0.5;
  }
}
