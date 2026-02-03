// "Remove redundant argument to call 'method(String)'" "true-preview"
class A {
  public A() {
    method(<caret>5, "abc");
  }

  private void method(String string) {
  }
}