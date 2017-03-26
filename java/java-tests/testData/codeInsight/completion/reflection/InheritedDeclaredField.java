class Main {
  void foo() {
    Test.class.getDeclaredField("<caret>");
  }
}

class Test extends Parent {
  public int num;
  int num3;
}

class Parent {
  public int num2;
  int num4;
}