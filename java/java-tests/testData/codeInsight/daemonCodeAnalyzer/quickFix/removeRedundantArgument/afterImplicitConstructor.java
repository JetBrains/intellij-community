// "Remove redundant argument to call 'A()'" "true-preview"
class A {
  private void method() {
    new A();
  }
}