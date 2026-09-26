// "Remove redundant arguments to call 'method(boolean, T...)'" "true-preview"
class A {
  public void foo() {
    method(true, 7);
  }

  private <T extends Number> void method(boolean b, T... ts) {
  }
}