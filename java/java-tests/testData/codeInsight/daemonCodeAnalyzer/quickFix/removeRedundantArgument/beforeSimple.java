// "Remove redundant arguments to call 'method(int, String)'" "true"
class A {
  public A() {
    method(5, "",<caret> 10);
  }

  private void method(int s, String s2) {
  }
}