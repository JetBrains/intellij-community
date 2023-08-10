// "Remove redundant arguments to call 'method(int, Integer)'" "true-preview"
class A {
  public A() {
    method(new Integer(5), 5);
  }

  private void method(int i, Integer i2) {
  }
}