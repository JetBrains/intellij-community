class Boxed {
  String method(boolean value) {
    if (returnsBool(value)) {
      return "foo";
    }
    return "baz";
  }

  public boolean returnsBool(boolean value) {
    return Math.random() > 0.5 ? true : false;
  }
}
