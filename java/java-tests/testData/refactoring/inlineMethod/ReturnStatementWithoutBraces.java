class JavaClass {
  String bar() {
    return "bar";
  }

  String baz(boolean condition) {
    if (condition)
      return b<caret>ar();

    return "default";
  }
}