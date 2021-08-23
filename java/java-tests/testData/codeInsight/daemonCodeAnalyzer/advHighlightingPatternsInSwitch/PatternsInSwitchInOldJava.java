class X {
  int switchTest(Object obj) {
    return switch (obj) {
      case <error descr="Guarded and parenthesized patterns are not supported at language level '16'">(String s)</error> -> 1;
      case <error descr="Guarded and parenthesized patterns are not supported at language level '16'">Integer i && predicate()</error> -> 2;
      case <error descr="Patterns in switch are not supported at language level '16'">Integer i</error> -> 3;
      case <error descr="Patterns in switch are not supported at language level '16'">default</error> -> 4;
      case <error descr="Patterns in switch are not supported at language level '16'">null</error> -> 10;
    };
  }

  int instanceofTest(Object obj) {
    if (obj instanceof (<error descr="Guarded and parenthesized patterns are not supported at language level '16'">Integer i && predicate()</error>)) {
      return 1;
    }
    if (obj instanceof <error descr="Guarded and parenthesized patterns are not supported at language level '16'">(String s)</error>) {
      return 3;
    }
    return 2;
  }

  native static boolean predicate();
}