// "Remove redundant arguments to call 'method(boolean, T...)'" "true-preview"
class A {
  public void foo() {
    method(<caret>"abc", true, 7, "def");
  }

  private <T extends Number> void method(boolean b, T... ts) {
  }
}