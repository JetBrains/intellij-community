class Main {
  void foo() {
    Test.class.getField("<caret>");
  }
}

class Test extends Parent {
  public int num;
  void int num3;
}

class Parent {
  public int num2;
  int num4;
}