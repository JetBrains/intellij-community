package pck;

class TestCase {
  void assertEquals(Object o1, Object o2) {}

  void assertEquals(int i1, int i2) {}
}

class Test extends TestCase {
  void test() {
    int expected = 1;
    Integer actual = 2;
    assertEquals<error descr="Ambiguous method call: both 'TestCase.assertEquals(Object, Object)' and 'TestCase.assertEquals(int, int)' match">(expected, actual)</error>;
  }
}