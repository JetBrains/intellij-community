class X {
  int switchTest1(Object obj) {
    return switch (obj) {
      case <error descr="Parenthesized patterns are not supported at language level '21'">(String s)</error> -> 1;
      case Integer i -> 3;
      case default -> 4;
      case null -> 10;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>() -> 5;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>(double x, double y) -> 6;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>() <error descr="Identifier is not allowed here">point</error> -> 7;
      case <error descr="Cannot resolve symbol 'Point'">Point</error>(double x, double y) <error descr="Identifier is not allowed here">point</error> -> 8;
    };
  }

  int switchTest2(int i) {
    return switch (i) {
        case <error descr="Incompatible types. Found: 'java.lang.Integer', required: 'int'">Integer integer</error> -> "int";
        case <error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">Object obj</error> -> "Object";
        default -> "not ok";
    };
  }

  void switchTest3(<error descr="Cannot resolve symbol 'Point'">Point</error><? extends String> point1, <error descr="Cannot resolve symbol 'Point'">Point</error><? super String> point2) {
    switch (point1) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><?>() -> {}
    }

    switch (point1) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><?>() <error descr="Identifier is not allowed here">point</error> -> {}
    }

    switch (point1) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><? extends String>() -> {}
    }

    switch (point1) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><? extends String>()  <error descr="Identifier is not allowed here">point</error> -> {}
    }

    switch (point2) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><? super String>() -> {}
    }

    switch (point2) {
      case <error descr="Cannot resolve symbol 'Point'">Point</error><? super String>() <error descr="Identifier is not allowed here">point</error> -> {}
    }
  }

  int instanceofTest(Object obj) {
    if (obj instanceof (Integer i<error descr="')' expected"> </error>&& predicate())<error descr="Statement expected"><error descr="Unexpected token">)</error></error> {
      return 1;
    }
    if (obj instanceof <error descr="Parenthesized patterns are not supported at language level '21'">(String s)</error>) {
      return 3;
    }
    return 2;
  }

  void unconditionalGuardAndDefault(Object obj) {
    switch (obj) {
      case <error descr="'switch' has both an unconditional pattern and a default label">Object o when true</error> -> {}
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> {}
    }
  }

  native static boolean predicate();
}