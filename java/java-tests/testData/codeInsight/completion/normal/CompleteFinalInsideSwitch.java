class Foo {
  public int foo(String str) {
    return switch (str) {
      case "x" -> fina<caret>
    };
  }
}
