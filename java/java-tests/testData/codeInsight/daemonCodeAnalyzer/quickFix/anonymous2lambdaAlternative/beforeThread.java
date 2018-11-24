// "Replace with new Thread(() -> {…})" "true"
public class Main {
  public void testThread() {
    new <caret>Thread() {
      // Comment outside
      @Override
      public void run() {
        // Comment inside
        System.out.println("Hello from thread!");
      }
      // Ending comment
    }.start();
  }
}