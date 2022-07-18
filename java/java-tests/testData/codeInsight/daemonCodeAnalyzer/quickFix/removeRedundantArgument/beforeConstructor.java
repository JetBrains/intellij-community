// "Remove redundant arguments to call 'A()'" "true"
class A {
  public A() { }

  private void method() {
    new A("<caret>");
  }
}