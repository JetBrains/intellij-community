// "Replace with new Thread(() -> {…})" "false"
public class Main {
  public void testThread() {
    new <caret>Thread() {
      @Override
      public void start() {
        System.out.println("Hello from thread!");
      }
    }.start();
  }
}