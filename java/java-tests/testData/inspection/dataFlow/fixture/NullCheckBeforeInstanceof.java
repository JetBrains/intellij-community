class Doo {
  final Object finalField;
  Object mutableField;

  Foo(Object finalField) {
    this.finalField = finalField;
  }

  Object method() { return new Object(); }

  public void foo(final Object o) {
    if (o != null && o instanceof String) {}
    if (finalField != null && o instanceof String) {}
    if (finalField != null) {
      if (finalField instanceof String) {}
    }
    if (finalField != null) {
      if (finalField instanceof String) {}
      System.out.println();
    }
    if (mutableField != null && mutableField instanceof String) {}
    if (method() != null && method() instanceof String) {}
  }
}