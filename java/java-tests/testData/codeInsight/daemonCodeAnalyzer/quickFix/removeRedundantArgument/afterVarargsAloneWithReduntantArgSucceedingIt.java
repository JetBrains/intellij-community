// "Remove redundant argument to call 'method(String...)'" "true-preview"
class A {
  public A() {
    method("abc", "def");
  }

  private void method(String... strings) {
  }
}