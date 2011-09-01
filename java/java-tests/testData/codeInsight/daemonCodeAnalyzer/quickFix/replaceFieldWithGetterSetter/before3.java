// "Replace with getter" "true"
class Test {
  private int i;

  public int getI() {
    return i;
  }
}

class Foo {
  void foo(Test t) {
    System.out.println(t.<caret>i);
  }
}