// "Replace with setter" "true"
class Test {
  private int i;

  public void setI(int i) {
    this.i = i;
  }
}

class Foo {
  void foo(Test t) {
    t.setI(0);
  }
}