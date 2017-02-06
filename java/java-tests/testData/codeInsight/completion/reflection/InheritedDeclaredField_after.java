class Main {
  void foo() {
    Test.class.getDeclaredField("num3");
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