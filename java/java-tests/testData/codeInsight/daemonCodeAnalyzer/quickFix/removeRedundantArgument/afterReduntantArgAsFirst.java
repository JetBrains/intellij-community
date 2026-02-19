// "Remove redundant argument to call 'method(String)'" "true-preview"
class A {
  public A() {
    method("abc");
  }

  private void method(String string) {
  }
}