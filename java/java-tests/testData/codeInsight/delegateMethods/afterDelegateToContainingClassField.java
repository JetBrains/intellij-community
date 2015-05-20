class A {
  void foo() {}
}

class Test {
  Runnable r = new Runnable() {
      public void foo() {
          a.foo();
      }

      public void run() {
      
    }
  };
  private A a;
  
}