class MyTest {
  private void test1(String s1, Object... objs) { }

  private void test2() {
    test1("", (<warning descr="Casting '\"\"' to 'String' is redundant">String</warning>)"");
  }
}

class MyTest1 {
  private void test1(String s1, Object... objs) { }

  private void test2() {
    test1("", (Object) new Object[0]);
  }
}