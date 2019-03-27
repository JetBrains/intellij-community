class C {
  String test() {
    while (true) {
      try {
        return get();
      }
      catch (Exception e) {}
      <selection>
      if (foo()) {
        return null;
      }
      if (bar()) {
        return null;
      }
      </selection>
    }
  }

  String get() { return null; }
  boolean foo() { return false; }
  boolean bar() { return false; }
}