class Test {
  @Deprecated
  protected Test() { }

  static <T extends Test> void foo() {}
}
