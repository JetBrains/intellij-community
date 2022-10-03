class X {
  int switchTest1(Object obj) {
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

  int switchTest2(int i) {
    return switch (i) {
        case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer integer</error> -> "int";
        case <error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">Object obj</error> -> "Object";
        default -> "not ok";
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

  void unconditionalGuardAndDefault(Object obj) {
    switch (obj) {
      case <error descr="'switch' has both a total pattern and a default label">Object o when true</error> -> {}
      <error descr="'switch' has both a total pattern and a default label">default</error> -> {}
    }
  }

  native static boolean predicate();
}