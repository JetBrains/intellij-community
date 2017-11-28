class Main {
  void foo() {
    Test.class.getMethod("<caret>");
  }
}

class Test {
  public void overloaded(){}
  public void overloaded(int n){}
}