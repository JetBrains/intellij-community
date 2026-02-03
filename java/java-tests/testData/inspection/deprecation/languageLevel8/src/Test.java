class Test {
  void test() {
    new Integer(10);
    deprecatedMethod();
  }
  
  @Deprecated(since="9")
  void deprecatedMethod() {}
}