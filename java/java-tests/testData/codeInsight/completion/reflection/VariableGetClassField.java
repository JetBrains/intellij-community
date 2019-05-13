class Main {
  Test test;
  void foo() {
    test.getClass().getField("<caret>");
  }
}

class Test {
  public int num;
  public int num2;
  int num3;
}