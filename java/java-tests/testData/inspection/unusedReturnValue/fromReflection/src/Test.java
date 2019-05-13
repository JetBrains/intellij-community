class Test {
  static String foo() {
    return "";
  }

  {
    Test.class.getMethod("foo");
  }
}
