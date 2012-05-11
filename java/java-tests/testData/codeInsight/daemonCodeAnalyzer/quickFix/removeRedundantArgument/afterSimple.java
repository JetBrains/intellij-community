// "Remove redundant arguments to match 'method(int, String)'" "true"
class A {
  public A() {
    method(5, "");
  }

  private void method(int s, String s2) {
  }
}