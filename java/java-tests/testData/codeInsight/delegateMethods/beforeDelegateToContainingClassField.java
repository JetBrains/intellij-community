class A {
  void foo() {}
}

class Test {
  Runnable r = new Runnable() {
    <caret>
    public void run() {
      
    }
  };
  private A a;
  
}