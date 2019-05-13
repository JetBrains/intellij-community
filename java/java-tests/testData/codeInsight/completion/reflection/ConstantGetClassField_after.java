class Main {
  final Parent test = new Test();
  void foo() {
    test.getClass().getField("num2");
  }
}

class Test extends Parent {
  public int num;
  int num2;
}

class Parent {
  public int num3;
  int num4;
}