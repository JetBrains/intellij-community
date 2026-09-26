// "Remove redundant arguments to call 'method(int, T)'" "true-preview"
class A {
  public A() {
    method(5, new Exception());
  }

  private <T extends Exception> void method(int i, T t) {
  }
}