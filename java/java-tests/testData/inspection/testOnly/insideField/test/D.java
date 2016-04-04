public class D {
  public static final Runnable MY_RUNNABLE = new Runnable() {
    public void run() {
      System.out.println(A.MY_FIELD);
    }
  };

  public void test() {
    new Runnable() {
      public void run() {
        System.out.println(A.MY_FIELD);
      }
    };
  }
}