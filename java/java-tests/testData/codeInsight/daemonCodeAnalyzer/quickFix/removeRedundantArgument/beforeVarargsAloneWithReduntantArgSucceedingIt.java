// "Remove redundant argument to call 'method(String...)'" "true-preview"
class A {
  public A() {
    method(<caret>"abc", "def", 5);
  }

  private void method(String... strings) {
  }
}