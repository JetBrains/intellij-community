// "Remove redundant argument to call 'method(int, String)'" "true-preview"
class A {
  public A() {
    method(<caret>5, "abc");
  }

  private void method(int i, String string) {
  }
}