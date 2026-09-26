// "Replace with getter" "true-preview"
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