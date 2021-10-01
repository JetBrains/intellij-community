class X {
  int switchTest(Object obj) {
    return switch (obj) {
      case <error descr="Pattern matching in switch are not supported at language level '16'">(String s)</error> -> 1;
      case <error descr="Pattern matching in switch are not supported at language level '16'">Integer i && predicate()</error> -> 2;
      case <error descr="Pattern matching in switch are not supported at language level '16'">Integer i</error> -> 3;
      case <error descr="Pattern matching in switch are not supported at language level '16'">default</error> -> 4;
    };
  }

  int instanceofTest(Object obj) {
    if (obj instanceof (<error descr="Pattern matching in switch are not supported at language level '16'">Integer i && predicate()</error>)) {
      return 1;
    }
    return 2;
  }

  native static boolean predicate();
}