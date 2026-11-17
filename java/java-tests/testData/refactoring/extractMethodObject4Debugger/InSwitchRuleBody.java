class Sample {
  static void foo(Object value) {
    switch (value) {
      case Integer i when i > 0 ->
        <caret>System.out.println(i);
      default -> {
      }
    }
  }
}