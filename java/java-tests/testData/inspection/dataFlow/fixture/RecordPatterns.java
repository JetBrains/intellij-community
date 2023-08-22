class Test {
  record R(int x, int y) {}
  record R1(Object obj) {}
  record Inner(String s) {}
  record Outer(Inner i1, Inner i2) {}

  void test1(Object obj) {
    switch (obj) {
      case R1(String s) -> {

      }
      case R1(Object o) -> {
        if (<warning descr="Condition 'o instanceof String' is always 'false'">o instanceof String</warning>) {}
      }
      default -> {
        if (<warning descr="Condition 'obj instanceof R1' is always 'false'">obj instanceof R1</warning>) {}
      }
    }
  }

  void test(Object obj) {
    switch(obj) {
      case R(int x, int y) -> {
        if (<warning descr="Condition 'obj == null' is always 'false'">obj == null</warning>) {}
      }
      default -> {
        if (<warning descr="Condition 'obj instanceof R' is always 'false'">obj instanceof R</warning>) {}
      }
    }
  }

  void testWhen(Object obj) {
    switch (obj) {
      case R(int a, int b) when (a > b) -> {}
      case R(int b, int a) when (<warning descr="Condition 'b > a' is always 'false'">b > a</warning>) -> {}
      case R(int x, int y) when <warning descr="Condition 'x == ((R)obj).x()' is always 'true'">x == ((R)obj).x()</warning> -> {}
      default -> {}
    }
  }

  void testNested(Object obj) {
    switch (obj) {
      case Outer(Inner(String s1), Inner(String s2)) when s1.isEmpty() || s2.isEmpty() -> {}
      case Outer o -> {
        Inner first = o.i1();
        Inner second = o.i2();
        if (first.s().isEmpty()) {} // TODO: report (requires less aggressive flushing)
        if (second.s().isEmpty()) {}
      }
      default -> {}
    }
  }
}