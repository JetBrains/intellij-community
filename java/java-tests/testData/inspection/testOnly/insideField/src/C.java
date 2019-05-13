public class C {
  @org.junit.Test
  public void foo() {
    new Runnable() {
      public void run() {
        System.out.println(A.MY_FIELD);
      }
    };
  }
}