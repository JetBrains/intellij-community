// "Remove redundant arguments to match 'method(int, Integer)'" "true"
class A {
  public A() {
    method(new Integer(5), 5,<caret> "", new String());
  }

  private void method(int i, Integer i2) {
  }
}