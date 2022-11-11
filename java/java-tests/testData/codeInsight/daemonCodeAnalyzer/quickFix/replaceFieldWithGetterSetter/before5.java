// "Replace with setter" "true-preview"
class Test {
  private int i;

  public void setI(int i) {
    this.i = i;
  }
}

class Foo {
  void foo(Test t) {
    t.<caret>i = 0;
  }
}