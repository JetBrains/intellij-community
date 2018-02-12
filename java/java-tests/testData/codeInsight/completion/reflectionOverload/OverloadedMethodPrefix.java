class Main {
  void foo() {
    Test.class.getDeclaredMethod("foo<caret>");
  }
}

class Test {
  public void foo(){}
  public void foo(int n){}
  public void foo(String s){}
}