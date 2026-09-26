class Constructors {
  class X {
    void foo() { }
    void bar(String a) { }
  }

  public void testMethod() throws Exception {
    X.class.getDeclaredMethod("foo");
    X.class.getDeclaredMethod("bar", String.class);
  }
}