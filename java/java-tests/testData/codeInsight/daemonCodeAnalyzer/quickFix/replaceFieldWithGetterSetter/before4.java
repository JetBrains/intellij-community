// "Replace with getter" "false"
class Test {
  private int i;

  public int getI() {
    return i;
  }
}

class Foo {
  void foo(Test t) {
    t.<caret>i = 0;
  }
}