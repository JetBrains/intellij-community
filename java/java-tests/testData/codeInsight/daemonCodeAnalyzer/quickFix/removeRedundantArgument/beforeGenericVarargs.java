// "Remove redundant argument to call 'method(int, T...)'" "true-preview"
class A {
  public void foo() {
    method(<caret>"abc", 1, "def", 2);
  }

  private <T> void method(int i, T... ts) {
  }
}