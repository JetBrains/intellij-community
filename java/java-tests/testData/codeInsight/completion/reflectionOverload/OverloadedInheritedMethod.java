class Main {
  void foo() {
    Test.class.getMethod("<caret>");
  }
}

class Test extends Parent {
  public static void overloaded(int n){}
}

class Parent {
  public static void overloaded(String s){}
}