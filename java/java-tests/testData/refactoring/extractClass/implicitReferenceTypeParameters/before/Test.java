class Test {
  public static <T> T foo() { return null; }

  {
    String s = true ? Test.<String>foo() : "";
  }
}