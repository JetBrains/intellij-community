class X {
  int switchTest(Object obj) {
    return switch (obj) {
      case (String <error descr="Patterns in switch are not supported at language level '16'">s</error>) -> 1;
      case Integer <error descr="Patterns in switch are not supported at language level '16'">i</error> when predicate() -> 2;
      case Integer <error descr="Patterns in switch are not supported at language level '16'">i</error> -> 3;
      case default -> 4;
      case <error descr="Patterns in switch are not supported at language level '16'">null</error> -> 10;
    };
  }

  void testCaseDefault(int num) {
    switch (num) {
      case <error descr="The label for the default case must only use the 'default' keyword, without 'case'">default</error> -> {}
    }
  }

  int instanceofTest(Object obj) {
    if (obj instanceof <error descr="Parenthesized patterns are not supported at language level '16'">(Integer i)</error> && predicate()) {
      return 1;
    }
    if (obj instanceof <error descr="Parenthesized patterns are not supported at language level '16'">(String s)</error>) {
      return 3;
    }
    return 2;
  }

  native static boolean predicate();
}