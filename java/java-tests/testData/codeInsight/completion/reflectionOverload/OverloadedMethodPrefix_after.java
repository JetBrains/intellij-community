class Main {
  void foo() {
    Test.class.getDeclaredMethod("foo", String.class);
  }
}

class Test {
  public void foo(){}
  public void foo(int n){}
  public void foo(String s){}
}