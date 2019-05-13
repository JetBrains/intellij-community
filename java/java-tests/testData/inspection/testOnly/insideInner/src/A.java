public class A {
  @org.jetbrains.annotations.TestOnly
  public void foo() {}

  @org.jetbrains.annotations.TestOnly
  public void test() {
    new Runnable() {
      public void run() {
        foo();
      }
    };
  }

  public void production() {
    new Runnable() {
      public void run() {
        foo();      
      }
    };
  }
}