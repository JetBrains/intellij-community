public class C {
  @org.junit.Test
  public void foo() {
    new Runnable() {
      public void run() {
        new A().foo();
      }
    };
  }
}