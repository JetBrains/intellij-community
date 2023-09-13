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
      case <error descr="'switch' has both an unconditional pattern and a default label">Object o</error> when true -> {}
      <error descr="'switch' has both an unconditional pattern and a default label">default</error> -> {}
    }
  }

  void dd6(String str) {
    switch (str) {
      case String i when i.length() == 2 -> System.out.println(2);
      case String i -> System.out.println(2);
    }
  }

  void dd7(String str) {
    switch (str) {
      case String i -> System.out.println(2);
      case <error descr="Label is dominated by a preceding case label 'String i'">String i</error> when i.length() == 2 -> System.out.println(2);
    }
  }

  int testC(String str) {
    switch (str) {
      case String s when s.isEmpty()==true: return 1;
      case "": return -1;
      default: return 0;
    }
  }

  int testC2(String str) {
    switch (str) {
      case String s: return 1;
      case <error descr="Label is dominated by a preceding case label 'String s'">""</error>: return -1;
    }
  }

  native static boolean predicate();

  sealed interface I1 {}
  sealed interface I2 {}
  record R1() implements I1 {}
  record R2() implements I2 {}
  record R3() implements I1, I2 {};

  public class Test22{
    public <T extends I1 & I2> void test(T c) {
      switch (c) {
        case <error descr="Incompatible types. Found: 'X.R2', required: 'T'">R2 r1</error> -> System.out.println(5);
        case R3 r1 -> System.out.println(1);
      }
    }
  }
}