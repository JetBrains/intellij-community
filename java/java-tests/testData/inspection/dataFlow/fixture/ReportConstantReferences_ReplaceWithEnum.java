class Test {
  private void test() {
    foo.MyEnum s = foo.MyEnum.FOO;
    System.out.println(<caret><warning descr="Value 's' is always 'FOO'">s</warning>);
  }

}