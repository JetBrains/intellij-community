class Sample {
  static int foo(Object value) {
    int r = switch (value) {
      case Integer i when i > 0 -> <caret>i * 2;
      default -> 0;
    };
    return r;
  }
}