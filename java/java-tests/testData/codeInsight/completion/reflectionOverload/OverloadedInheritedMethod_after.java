class Main {
  void foo() {
    Test.class.getMethod("overloaded", String.class);
  }
}

class Test extends Parent {
  public static void overloaded(int n){}
}

class Parent {
  public static void overloaded(String s){}
}