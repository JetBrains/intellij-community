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

  // just don't warn about boxed booleans at all
  private static void yes() {
    Boolean b = Boolean.TRUE;
    if (Boolean.TRUE.equals(b)) {
      System.out.println("no");
    }
  }
}
