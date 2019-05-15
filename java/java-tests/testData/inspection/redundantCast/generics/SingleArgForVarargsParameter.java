class MyTest {
  private void test1(String s1, Object... objs) { }

  private void test2() {
    test1("", (<warning descr="Casting '\"\"' to 'String' is redundant">String</warning>)"");
  }
}