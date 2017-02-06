class Main {
  void foo() {
    bar().getClass().getField("<caret>");
  }
  Test bar() { return null; }
}

class Test {
  public int num;
  public int num2;
  int num3;
}