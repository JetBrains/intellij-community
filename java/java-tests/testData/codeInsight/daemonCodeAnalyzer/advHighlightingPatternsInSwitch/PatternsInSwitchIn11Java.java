class X {
  int switchTest(Object obj) {
    return switch (obj) {
      case (String <error descr="Patterns in switch are not supported at language level '11'">s</error>) -> 1;
      case Integer <error descr="Patterns in switch are not supported at language level '11'">i</error> && predicate() -> 2;
      case Integer <error descr="Patterns in switch are not supported at language level '11'">i</error> -> 3;
      case <error descr="Patterns in switch are not supported at language level '11'">default</error> -> 4;
      case <error descr="Patterns in switch are not supported at language level '11'">null</error> -> 10;
    };
  }

  int instanceofTest(Object obj) {
    if (obj instanceof (Integer <error descr="Patterns in 'instanceof' are not supported at language level '11'">i</error> && predicate())) {
      return 1;
    }
    if (obj instanceof (String <error descr="Patterns in 'instanceof' are not supported at language level '11'">s</error>)) {
      return 3;
    }
    return 2;
  }

  native static boolean predicate();
}