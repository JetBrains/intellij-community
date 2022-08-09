// "Remove redundant arguments to call 'A()'" "true-preview"
class A {
  public A() { }

  private void method() {
    new A();
  }
}