class Foo {
  static final String HEL<caret>LO = createHelloText();

  static String createHelloText() {
    return "hello";
  }
}

