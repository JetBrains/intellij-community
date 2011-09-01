// "Replace with getter" "false"
class Test {
  private int i;
}

class Foo {
  void foo(Test t) {
    System.out.println(t.<caret>i);
  }
}