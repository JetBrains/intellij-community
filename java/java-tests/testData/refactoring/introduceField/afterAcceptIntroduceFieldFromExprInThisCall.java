class Test {
    public static final String foo = "foo";

    Test(String s) {}
  Test() {
    this(foo);
  }
}