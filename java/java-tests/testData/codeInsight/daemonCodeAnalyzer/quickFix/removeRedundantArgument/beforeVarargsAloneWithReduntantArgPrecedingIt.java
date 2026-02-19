// "Remove redundant argument to call 'method(String...)'" "true-preview"
class A {
  public A() {
    method(<caret>5, "abc", "def");
  }

  private void method(String... strings) {
  }
}