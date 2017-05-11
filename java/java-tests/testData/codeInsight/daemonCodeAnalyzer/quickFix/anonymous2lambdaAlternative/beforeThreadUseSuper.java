// "Replace with new Thread(() -> {…})" "false"
public class Main {
  public void testThread() {
    new <caret>Thread() {
      @Override
      public void run() {
        System.out.println("Hello from thread!");
        super.run();
      }
    }.start();
  }
}