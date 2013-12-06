package pkg;

public class LocalClass {
  public Runnable runnable() {
    class MyRunnable implements Runnable {
      public void run() {
        System.out.println(this);
      }
    }

    return new MyRunnable();
  }
}
