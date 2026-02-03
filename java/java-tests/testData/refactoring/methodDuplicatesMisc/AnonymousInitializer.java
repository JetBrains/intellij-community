public class Model {
  public static final Runnable FO<caret>O = new Runnable() {
    public void run() {
      System.out.println("abc");
    }
  };

  public void foo() {
    new Runnable() {
      public void run() {
        System.out.println("abc");
      }
    }.run();
  }
}
