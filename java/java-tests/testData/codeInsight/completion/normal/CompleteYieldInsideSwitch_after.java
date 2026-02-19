class Foo {
  public int foo(String str) {
    return switch (str) {
      case "x" -> {
          yield <caret>
      }
    };
  }
}
