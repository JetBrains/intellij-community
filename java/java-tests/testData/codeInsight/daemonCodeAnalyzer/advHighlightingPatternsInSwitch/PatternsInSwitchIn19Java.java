class X {
  int switchTest(Object obj) {
    return switch (obj) {
      case <error descr="Old patterns from JEP 406 are not available since Java 19 preview">(String s)</error> -> 1;
      case <error descr="Old patterns from JEP 406 are not available since Java 19 preview">Integer i && predicate()</error> -> 2;
      case Integer i -> 3;
      case default -> 4;
      case null -> 10;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>() point -> 5;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>(double x, double y) -> 6;
    };
  }

  int instanceofTest(Object obj) {
    if (obj instanceof (<error descr="Old patterns from JEP 406 are not available since Java 19 preview">Integer i && predicate()</error>)) {
      return 1;
    }
    if (obj instanceof <error descr="Old patterns from JEP 406 are not available since Java 19 preview">(String s)</error>) {
      return 3;
    }
    return 2;
  }

  native static boolean predicate();
}