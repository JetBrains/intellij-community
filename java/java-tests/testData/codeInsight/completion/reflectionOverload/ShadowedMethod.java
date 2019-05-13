class Main {
  void foo() {
    Test.class.getMethod("<caret>");
  }
}

class Test extends Parent {
  public static void shadowed(){}
}

class Parent {
  public static void shadowed(){}
}