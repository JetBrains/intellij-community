class Main {
  void foo() {
    Test.class.getMethod("overloaded", int.class);
  }
}

class Test {
  public void overloaded(){}
  public void overloaded(int n){}
}